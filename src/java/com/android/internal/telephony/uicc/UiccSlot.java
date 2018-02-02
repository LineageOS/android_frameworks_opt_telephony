/*
 * Copyright 2017 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.Rlog;
import android.util.LocalLog;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * This class represents a physical slot on the device.
 */
public class UiccSlot extends Handler {
    private static final String TAG = "UiccSlot";
    private static final boolean DBG = true;

    public static final String EXTRA_ICC_CARD_ADDED =
            "com.android.internal.telephony.uicc.ICC_CARD_ADDED";

    private final Object mLock = new Object();
    private boolean mActive;
    private CardState mCardState;
    private Context mContext;
    private CommandsInterface mCi;
    private UiccCard mUiccCard;
    private RadioState mLastRadioState = RadioState.RADIO_UNAVAILABLE;
    private boolean mIsEuicc;
    private String mIccId;
    private Integer mPhoneId = null;

    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARD_ADDED = 14;

    private static final LocalLog sLocalLog = new LocalLog(100);

    public UiccSlot(Context c, boolean isActive) {
        if (DBG) log("Creating");
        mContext = c;
        mActive = isActive;
        mCardState = CardState.CARDSTATE_ABSENT;
    }

    /**
     * Update slot. The main trigger for this is a change in the ICC Card status.
     */
    public void update(CommandsInterface ci, IccCardStatus ics, int phoneId) {
        synchronized (mLock) {
            CardState oldState = mCardState;
            mCardState = ics.mCardState;
            mPhoneId = phoneId;
            parseAtr(ics.atr);
            mCi = ci;

            RadioState radioState = mCi.getRadioState();
            if (DBG) {
                log("update: radioState=" + radioState + " mLastRadioState=" + mLastRadioState);
            }

            if (oldState != CardState.CARDSTATE_ABSENT
                    && mCardState == CardState.CARDSTATE_ABSENT) {
                // No notifications while radio is off or we just powering up
                if (radioState == RadioState.RADIO_ON && mLastRadioState == RadioState.RADIO_ON) {
                    if (DBG) log("update: notify card removed");
                    sendMessage(obtainMessage(EVENT_CARD_REMOVED, null));
                }

                UiccProfile.broadcastInternalIccStateChangedIntent(
                        IccCardConstants.INTENT_VALUE_ICC_ABSENT, null, mPhoneId);

                // no card present in the slot now; dispose card and make mUiccCard null
                mUiccCard.dispose();
                mUiccCard = null;
            } else if (oldState == CardState.CARDSTATE_ABSENT
                    && mCardState != CardState.CARDSTATE_ABSENT) {
                // No notifications while radio is off or we just powering up
                if (radioState == RadioState.RADIO_ON && mLastRadioState == RadioState.RADIO_ON) {
                    if (DBG) log("update: notify card added");
                    sendMessage(obtainMessage(EVENT_CARD_ADDED, null));
                }

                // card is present in the slot now; create new mUiccCard
                if (mUiccCard != null) {
                    loge("update: mUiccCard != null when card was present; disposing it now");
                    mUiccCard.dispose();
                }

                if (!mIsEuicc) {
                    mUiccCard = new UiccCard(mContext, mCi, ics, mPhoneId);
                } else {
                    // todo: initialize new EuiccCard object here
                    //mUiccCard = new EuiccCard();
                }
            } else {
                if (mUiccCard != null) {
                    mUiccCard.update(mContext, mCi, ics);
                }
            }
            mLastRadioState = radioState;
        }
    }

    /**
     * Update slot based on IccSlotStatus.
     */
    public void update(CommandsInterface ci, IccSlotStatus iss) {
        log("slotStatus update");
        synchronized (mLock) {
            mCi = ci;
            if (iss.slotState == IccSlotStatus.SlotState.SLOTSTATE_INACTIVE) {
                if (mActive) {
                    mActive = false;
                    mLastRadioState = RadioState.RADIO_UNAVAILABLE;
                    mPhoneId = null;
                    if (mUiccCard != null) mUiccCard.dispose();
                    mUiccCard = null;
                }
                parseAtr(iss.atr);
                mCardState = iss.cardState;
                mIccId = iss.iccid;
            } else if (!mActive && iss.slotState == IccSlotStatus.SlotState.SLOTSTATE_ACTIVE) {
                mActive = true;
                // todo - ignoring these fields for now; relying on sim state changed to update
                // these
                //      iss.atr;
                //      iss.cardState;
                //      iss.iccid;
                //      iss.logicalSlotIndex;
            }
        }
    }

    private void parseAtr(String atr) {
        // todo - parse atr and set mIsEuicc based on it
        mIsEuicc = false;
    }

    public boolean isEuicc() {
        return mIsEuicc;
    }

    public boolean isActive() {
        return mActive;
    }

    public String getIccId() {
        if (mIccId != null) {
            return mIccId;
        } else if (mUiccCard != null) {
            return mUiccCard.getIccId();
        } else {
            return null;
        }
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccSlot finalized");
    }

    private void onIccSwap(boolean isAdded) {

        boolean isHotSwapSupported = mContext.getResources().getBoolean(
                R.bool.config_hotswapCapable);

        if (isHotSwapSupported) {
            log("onIccSwap: isHotSwapSupported is true, don't prompt for rebooting");
            return;
        }
        log("onIccSwap: isHotSwapSupported is false, prompt for rebooting");

        promptForRestart(isAdded);
    }

    private void promptForRestart(boolean isAdded) {
        synchronized (mLock) {
            final Resources res = mContext.getResources();
            final String dialogComponent = res.getString(
                    R.string.config_iccHotswapPromptForRestartDialogComponent);
            if (dialogComponent != null) {
                Intent intent = new Intent().setComponent(ComponentName.unflattenFromString(
                        dialogComponent)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_ICC_CARD_ADDED, isAdded);
                try {
                    mContext.startActivity(intent);
                    return;
                } catch (ActivityNotFoundException e) {
                    loge("Unable to find ICC hotswap prompt for restart activity: " + e);
                }
            }

            // TODO: Here we assume the device can't handle SIM hot-swap
            //      and has to reboot. We may want to add a property,
            //      e.g. REBOOT_ON_SIM_SWAP, to indicate if modem support
            //      hot-swap.
            DialogInterface.OnClickListener listener = null;


            // TODO: SimRecords is not reset while SIM ABSENT (only reset while
            //       Radio_off_or_not_available). Have to reset in both both
            //       added or removed situation.
            listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    synchronized (mLock) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            if (DBG) log("Reboot due to SIM swap");
                            PowerManager pm = (PowerManager) mContext
                                    .getSystemService(Context.POWER_SERVICE);
                            pm.reboot("SIM is added.");
                        }
                    }
                }

            };

            Resources r = Resources.getSystem();

            String title = (isAdded) ? r.getString(R.string.sim_added_title) :
                    r.getString(R.string.sim_removed_title);
            String message = (isAdded) ? r.getString(R.string.sim_added_message) :
                    r.getString(R.string.sim_removed_message);
            String buttonTxt = r.getString(R.string.sim_restart_button);

            AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(buttonTxt, listener)
                    .create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_CARD_REMOVED:
                onIccSwap(false);
                break;
            case EVENT_CARD_ADDED:
                onIccSwap(true);
                break;
            default:
                loge("Unknown Event " + msg.what);
        }
    }

    /**
     * Returns the state of the UiccCard in the slot.
     * @return
     */
    public CardState getCardState() {
        synchronized (mLock) {
            return mCardState;
        }
    }

    /**
     * Returns the UiccCard in the slot.
     */
    public UiccCard getUiccCard() {
        synchronized (mLock) {
            return mUiccCard;
        }
    }

    /**
     * Processes radio state unavailable event
     */
    public void onRadioStateUnavailable() {
        if (mUiccCard != null) {
            mUiccCard.dispose();
        }
        mUiccCard = null;

        if (mPhoneId != null) {
            UiccProfile.broadcastInternalIccStateChangedIntent(
                    IccCardConstants.INTENT_VALUE_ICC_UNKNOWN, null, mPhoneId);
        }

        mCardState = CardState.CARDSTATE_ABSENT;
        mLastRadioState = RadioState.RADIO_UNAVAILABLE;
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(TAG, msg);
    }

    private void loglocal(String msg) {
        if (DBG) sLocalLog.log(msg);
    }

    /**
     * Dump
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccSlot:");
        pw.println(" mCi=" + mCi);
        pw.println(" mActive=" + mActive);
        pw.println(" mLastRadioState=" + mLastRadioState);
        pw.println(" mCardState=" + mCardState);
        if (mUiccCard != null) {
            pw.println(" mUiccCard=" + mUiccCard);
            mUiccCard.dump(fd, pw, args);
        } else {
            pw.println(" mUiccCard=null");
        }
        pw.println();
        pw.flush();
        pw.println("sLocalLog:");
        sLocalLog.dump(fd, pw, args);
        pw.flush();
    }
}
