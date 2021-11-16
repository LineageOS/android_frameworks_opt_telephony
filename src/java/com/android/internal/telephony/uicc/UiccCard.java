/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.euicc.EuiccCard;
import com.android.internal.telephony.uicc.euicc.EuiccPort;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;

/**
 * {@hide}
 */
public class UiccCard {
    protected static final String LOG_TAG = "UiccCard";
    protected static final boolean DBG = true;

    public static final String EXTRA_ICC_CARD_ADDED =
            "com.android.internal.telephony.uicc.ICC_CARD_ADDED";

    // The lock object is created by UiccSlot that owns this UiccCard - this is to share the lock
    // between UiccSlot, UiccCard, EuiccCard, UiccPort, EuiccPort and UiccProfile for now.
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    protected final Object mLock;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private CardState mCardState;
    protected String mCardId;
    private String mIccid;

    protected HashMap<Integer, UiccPort> mUiccPorts;
    private HashMap<Integer, Integer> mPhoneIdToPortIdx;

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId, Object lock) {
        if (DBG) log("Creating");
        mCardState = ics.mCardState;
        mLock = lock;
        mUiccPorts = new HashMap<>();
        mPhoneIdToPortIdx = new HashMap<>();
        update(c, ci, ics, phoneId);
    }

    /**
     * Dispose the card and its related UiccPort objects.
     */
    public void dispose() {
        synchronized (mLock) {
            if (DBG) log("Disposing card");
            for (UiccPort uiccPort : mUiccPorts.values()) {
                uiccPort.dispose();
            }
            mUiccPorts.clear();
            mUiccPorts = null;
            mPhoneIdToPortIdx.clear();
            mPhoneIdToPortIdx = null;
        }
    }

    /**
     * Update card. The main trigger for this is a change in the ICC Card status.
     */
    public void update(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        synchronized (mLock) {
            mCardState = ics.mCardState;
            mIccid = ics.iccid;
            updateCardId();
            if (mCardState != CardState.CARDSTATE_ABSENT) {
                int portIdx = 0; // TODO get ics.portId from IccCardStatus after new HAL changes
                UiccPort port = mUiccPorts.get(portIdx);
                if (port == null) {
                    if (this instanceof EuiccCard) {
                        port = new EuiccPort(c, ci, ics, phoneId, mLock, this); // eSim
                    } else {
                        port = new UiccPort(c, ci, ics, phoneId, mLock, this); // pSim
                    }
                    mUiccPorts.put(portIdx, port);
                } else {
                    port.update(c, ci, ics, this);
                }
                mPhoneIdToPortIdx.put(phoneId, 0/*ics.portId*/); //TODO modify after new HAL change
            } else {
                throw new RuntimeException("Card state is absent when updating!");
            }
        }
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccCard finalized");
    }

    /**
     * Updates the ID of the SIM card.
     *
     * <p>Whenever the {@link UiccCard#update(Context, CommandsInterface, IccCardStatus, int)}
     * is called, this function needs to be called to update the card ID. Subclasses of
     * {@link UiccCard} could override this function to set the {@link UiccCard#mCardId} to be
     * something else instead of {@link UiccCard#mIccid}.</p>
     */
    protected void updateCardId() {
        mCardId = mIccid;
    }

    @UnsupportedAppUsage
    public CardState getCardState() {
        synchronized (mLock) {
            return mCardState;
        }
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public String getIccId() {
        //TODO As part of MEP refactor, check the caller and modify logic to call UiccPort getIccId
        if (mIccid != null) {
            return mIccid;
        } else { //TODO if caller is not changed, modify else part logic to get iccId from UiccPort
            return null;
        }
    }

    /**
     * Returns the ID of this SIM card, it is the ICCID of the active profile on the card for a UICC
     * card or the EID of the card for an eUICC card.
     */
    public String getCardId() {
        if (!TextUtils.isEmpty(mCardId)) {
            return mCardId;
        } else {
            UiccProfile uiccProfile = mUiccPorts.get(TelephonyManager.DEFAULT_PORT_INDEX)
                    .getUiccProfile();
            return uiccProfile == null ? null : uiccProfile.getIccId();
        }
    }

    /**
     * Returns all the UiccPorts associated with the card.
     */
    public UiccPort[] getUiccPortList() {
        synchronized (mLock) {
            return mUiccPorts.values().stream().toArray(UiccPort[]::new);
        }
    }

    /**
     * Returns the UiccPort associated with the given phoneId
     */
    public UiccPort getUiccPortForPhone(int phoneId) {
        synchronized (mLock) {
            return mUiccPorts.get(mPhoneIdToPortIdx.get(phoneId));
        }
    }

    /**
     * Returns the UiccPort associated with the given port index.
     */
    public UiccPort getUiccPort(int portIdx) {
        synchronized (mLock) {
            return mUiccPorts.get(portIdx);
        }
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccCard:");
        pw.println(" mCardState=" + mCardState);
        pw.println(" mCardId=" + mCardId);
        pw.println(" mNumberOfPorts=" + mUiccPorts.size());
        pw.println();
        for (UiccPort uiccPort : mUiccPorts.values()) {
            uiccPort.dump(fd, pw, args);
        }
    }
}
