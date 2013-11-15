/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.telephony.thirdpartyphone;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import com.android.internal.telephony.Connection;

class ThirdPartyPostDialHelper {
    private static final String TAG = "ThirdPartyPostDialHlp";
    private static Boolean DBG = false;

    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_NEXT_POST_DIAL = 3;

    static final int PAUSE_DELAY_MILLIS = 3 * 1000;

    private String mPostDialString;
    private int mNextPostDialChar;
    private Connection.PostDialState mPostDialState = Connection.PostDialState.NOT_STARTED;
    private ThirdPartyConnection mConnection;

    public ThirdPartyPostDialHelper(ThirdPartyConnection connection, String postDialString) {
        mConnection = connection;
        mPostDialString = postDialString;
        if (DBG) log("new ThirdPartyPostDialHelper, postDialString: " + postDialString);
    }

    public void onCallEstablished() {
        processNextPostDialChar();
    }

    public Connection.PostDialState getPostDialState() {
        return mPostDialState;
    }

    public String getRemainingPostDialString() {
        if (mPostDialState == Connection.PostDialState.CANCELLED
            || mPostDialState == Connection.PostDialState.COMPLETE
            || mPostDialString == null
            || mPostDialString.length() <= mNextPostDialChar) {
            return "";
        }
        return mPostDialString.substring(mNextPostDialChar);
    }

    public void proceedAfterWaitChar() {
        if (mPostDialState != Connection.PostDialState.WAIT) {
            log("proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " +
                    mPostDialState);
            return;
        }

        setPostDialState(Connection.PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (mPostDialState != Connection.PostDialState.WILD) {
            log("proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " +
                    mPostDialState);
            return;
        }

        setPostDialState(Connection.PostDialState.STARTED);

        // Make a new postDialString, with the wild char replacement string
        // at the beginning, followed by the remaining postDialString.
        StringBuilder buf = new StringBuilder(str);
        buf.append(mPostDialString.substring(mNextPostDialChar));
        mPostDialString = buf.toString();
        mNextPostDialChar = 0;
        if (DBG) {
            log("proceedAfterWildChar: new postDialString is " + mPostDialString);
        }

        processNextPostDialChar();
    }

    public void cancelPostDial() {
        setPostDialState(Connection.PostDialState.CANCELLED);
    }

    private void setPostDialState(Connection.PostDialState s) {
        if (DBG) log("setPostDialState: " + mPostDialState + " -> " + s);
        mPostDialState = s;
    }

    private void processNextPostDialChar() {
        if (mPostDialState == Connection.PostDialState.CANCELLED) {
            return;
        }

        char nextChar = 0;
        if (mPostDialString == null || mPostDialString.length() <= mNextPostDialChar) {
            setPostDialState(Connection.PostDialState.COMPLETE);
        } else {
            setPostDialState(Connection.PostDialState.STARTED);
            nextChar = mPostDialString.charAt(mNextPostDialChar++);
            if (!processPostDialChar(nextChar)) {
                // Will call processNextPostDialChar
                mHandler.obtainMessage(EVENT_NEXT_POST_DIAL).sendToTarget();
                // Don't notify application
                log("processNextPostDialChar: c=" + nextChar + " isn't valid!");
                return;
            }
        }

        ThirdPartyPhone phone = (ThirdPartyPhone) mConnection.getCall().getPhone();
        Registrant postDialHandler = phone.getPostDialHandler();
        if (postDialHandler == null) {
            return;
        }
        Message notifyMessage = postDialHandler.messageForRegistrant();
        if (notifyMessage == null) {
            return;
        }

        AsyncResult ar = AsyncResult.forMessage(notifyMessage);
        // result is the Connection object
        ar.result = mConnection;
        ar.userObj = mPostDialState;
        // arg1 is the char being processed, 0 if complete.
        notifyMessage.arg1 = nextChar;
        notifyMessage.sendToTarget();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            mConnection.sendDtmf(c, mHandler.obtainMessage(EVENT_DTMF_DONE));
        } else if (c == PhoneNumberUtils.PAUSE) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_PAUSE_DONE),
                    PAUSE_DELAY_MILLIS);
        } else if (c == PhoneNumberUtils.WAIT) {
            setPostDialState(Connection.PostDialState.WAIT);
        } else if (c == PhoneNumberUtils.WILD) {
            setPostDialState(Connection.PostDialState.WILD);
        } else {
            return false;
        }
        return true;
    }

    private final Handler mHandler = new Handler() {
        @Override public void handleMessage(Message msg) {
            if (DBG) log("mHandler.handleMessage: " + msg);
            switch (msg.what) {
                case EVENT_NEXT_POST_DIAL:
                case EVENT_DTMF_DONE:
                case EVENT_PAUSE_DONE:
                    processNextPostDialChar();
                    break;
            }
        }
    };

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }
}
