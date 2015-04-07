/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.telephony.PhoneConstants;

import android.net.NetworkRequest;
import android.os.Message;
import android.telephony.Rlog;

public class DcSwitchAsyncChannel extends AsyncChannel {
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private static final String LOG_TAG = "DcSwitchAsyncChannel";

    private int tagId = 0;
    private DcSwitchStateMachine mDcSwitchState;

    // ***** Event codes for driving the state machine
    private static final int BASE = Protocol.BASE_DATA_CONNECTION_TRACKER + 0x00002000;
    static final int REQ_CONNECT =                    BASE + 0;
    static final int REQ_RETRY_CONNECT  =             BASE + 1;
    static final int REQ_DISCONNECT_ALL =             BASE + 2;
    static final int REQ_IS_IDLE_STATE =              BASE + 3;
    static final int RSP_IS_IDLE_STATE =              BASE + 4;
    static final int REQ_IS_IDLE_OR_DETACHING_STATE = BASE + 5;
    static final int RSP_IS_IDLE_OR_DETACHING_STATE = BASE + 6;
    static final int EVENT_DATA_ATTACHED =            BASE + 7;
    static final int EVENT_DATA_DETACHED =            BASE + 8;

    private static final int CMD_TO_STRING_COUNT = EVENT_DATA_DETACHED - BASE + 1;
    private static String[] sCmdToString = new String[CMD_TO_STRING_COUNT];
    static {
        sCmdToString[REQ_CONNECT - BASE] = "REQ_CONNECT";
        sCmdToString[REQ_RETRY_CONNECT - BASE] = "REQ_RETRY_CONNECT";
        sCmdToString[REQ_DISCONNECT_ALL - BASE] = "REQ_DISCONNECT_ALL";
        sCmdToString[REQ_IS_IDLE_STATE - BASE] = "REQ_IS_IDLE_STATE";
        sCmdToString[RSP_IS_IDLE_STATE - BASE] = "RSP_IS_IDLE_STATE";
        sCmdToString[REQ_IS_IDLE_OR_DETACHING_STATE - BASE] = "REQ_IS_IDLE_OR_DETACHING_STATE";
        sCmdToString[RSP_IS_IDLE_OR_DETACHING_STATE - BASE] = "RSP_IS_IDLE_OR_DETACHING_STATE";
        sCmdToString[EVENT_DATA_ATTACHED - BASE] = "EVENT_DATA_ATTACHED";
        sCmdToString[EVENT_DATA_DETACHED - BASE] = "EVENT_DATA_DETACHED";
    }

    public static class RequestInfo {
        boolean executed;
        NetworkRequest request;
        int priority;

        public RequestInfo(NetworkRequest request, int priority) {
            this.request = request;
            this.priority = priority;
        }

        @Override
        public String toString() {
            return "[ request=" + request + ", executed=" + executed +
                ", priority=" + priority + "]";
        }
    }

    protected static String cmdToString(int cmd) {
        cmd -= BASE;
        if ((cmd >= 0) && (cmd < sCmdToString.length)) {
            return sCmdToString[cmd];
        } else {
            return AsyncChannel.cmdToString(cmd + BASE);
        }
    }

    public DcSwitchAsyncChannel(DcSwitchStateMachine dcSwitchState, int id) {
        mDcSwitchState = dcSwitchState;
        tagId = id;
    }

    public int connect(RequestInfo apnRequest) {
        sendMessage(REQ_CONNECT, apnRequest);
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    public void retryConnect() {
        sendMessage(REQ_RETRY_CONNECT);
    }

    public int disconnectAll() {
        sendMessage(REQ_DISCONNECT_ALL);
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    public void notifyDataAttached() {
        sendMessage(EVENT_DATA_ATTACHED);
        if (DBG) log("notifyDataAttached");
    }

    public void notifyDataDetached() {
        sendMessage(EVENT_DATA_DETACHED);
        if (DBG) log("EVENT_DATA_DETACHED");
    }

    private boolean rspIsIdle(Message response) {
        boolean retVal = response.arg1 == 1;
        if (DBG) log("rspIsIdle=" + retVal);
        return retVal;
    }

    public boolean isIdleSync() {
        Message response = sendMessageSynchronously(REQ_IS_IDLE_STATE);
        if ((response != null) && (response.what == RSP_IS_IDLE_STATE)) {
            return rspIsIdle(response);
        } else {
            if (DBG) log("rspIsIndle error response=" + response);
            return false;
        }
    }

    public void reqIsIdleOrDetaching() {
        sendMessage(REQ_IS_IDLE_OR_DETACHING_STATE);
        if (DBG) log("reqIsIdleOrDetaching");
    }

    public boolean rspIsIdleOrDetaching(Message response) {
        boolean retVal = response.arg1 == 1;
        if (DBG) log("rspIsIdleOrDetaching=" + retVal);
        return retVal;
    }

    public boolean isIdleOrDetachingSync() {
        Message response = sendMessageSynchronously(REQ_IS_IDLE_OR_DETACHING_STATE);
        if ((response != null) && (response.what == RSP_IS_IDLE_OR_DETACHING_STATE)) {
            return rspIsIdleOrDetaching(response);
        } else {
            if (DBG) log("rspIsIdleOrDetaching error response=" + response);
            return false;
        }
    }

    @Override
    public String toString() {
        return mDcSwitchState.getName();
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, "[DcSwitchAsyncChannel-" + tagId + "]: " + s);
    }

}
