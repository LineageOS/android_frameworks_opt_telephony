/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony;

/**
 * Connection information for SRVCC
 */
public class SrvccConnection {
    private static final String TAG = "SrvccConnection";

    private static final int CALL_TYPE_NORMAL = 0;
    private static final int CALL_TYPE_EMERGENCY = 1;

    public static final int SUBSTATE_NONE = 0;
    /** Pre-alerting state. Applicable for MT calls only */
    public static final int SUBSTATE_PREALERTING = 1;

    public static final int TONE_NONE = 0;
    public static final int TONE_LOCAL = 1;
    public static final int TONE_NETWORK = 2;

    /** Values are CALL_TYPE_ */
    private int mType;

    /** Values are Call.State */
    private Call.State mState;

    /** Values are SUBSTATE_ */
    private int mSubstate;

    /** Values are TONE_ */
    private int mRingbackToneType;

    /** true if it is a multi-party call */
    private boolean mIsMpty;

    /** true if it is a mobile terminated call */
    private boolean mIsMT;

    /** Remote party nummber */
    private String mNumber;

    /** Values are PhoneConstants.PRESENTATION_ */
    private int mNumPresentation;

    /** Remote party name */
    private String mName;

    /** Values are PhoneConstants.PRESENTATION_ */
    private int mNamePresentation;

    public SrvccConnection(Connection c, boolean isEmergency, int ringbackToneType) {
        mType = isEmergency ? CALL_TYPE_EMERGENCY : CALL_TYPE_NORMAL;
        mState = c.getState();
        mSubstate = SUBSTATE_NONE;
        mRingbackToneType = ringbackToneType;
        mIsMpty = false;
        mIsMT = c.isIncoming();
        mNumber = c.getAddress();
        mNumPresentation = c.getNumberPresentation();
        mName = c.getCnapName();
        mNamePresentation = c.getCnapNamePresentation();
    }

    /** Returns the type of the call */
    public int getType() {
        return mType;
    }

    /** Returns the state */
    public Call.State getState() {
        return mState;
    }

    /** Returns the sub state */
    public int getSubState() {
        return mSubstate;
    }

    /** Returns the ringback tone type */
    public int getRingbackToneType() {
        return mRingbackToneType;
    }

    /** true if it is a multi-party call */
    public boolean isMultiParty() {
        return mIsMpty;
    }

    /** true if it is a mobile terminated call */
    public boolean isIncoming() {
        return mIsMT;
    }

    /** Returns the remote party nummber */
    public String getNumber() {
        return mNumber;
    }

    /** Returns the number presentation */
    public int getNumberPresentation() {
        return mNumPresentation;
    }

    /** Returns the remote party name */
    public String getName() {
        return mName;
    }

    /** Returns the name presentation */
    public int getNamePresentation() {
        return mNamePresentation;
    }
}
