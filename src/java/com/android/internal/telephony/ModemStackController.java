/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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
 *
 */

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubInfoRecord;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.Subscription.SubscriptionStatus;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/*
 * ModemStackController: Utility to provide the Stack capabilites and binding/unbinding
 * of Stacks based on client preference.
 * Includes:
 *    CurrentStackIds
 *    PreferredStackIds
 *    ModemCapabilities
 * Provides:
 *    Crossmapping (i.e. Binding/unbinding of stacks)
 *    RAT Capabilities of Stack for a sub.
 *    Current stack for a sub
 * Notifies:
 *    Stack Ready after update binding is completed to registrants.
 */

public class ModemStackController extends Handler {
    static final String LOG_TAG = "ModemStackController";

    //Utility class holding modem capabilities information per stack.
    public class ModemCapabilityInfo {
        private int mStackId;
        //bit vector mask of all suported RATs
        private int mSupportedRatBitMask;
        private int mVoiceDataCap;
        private int mMaxDataCap;

        public ModemCapabilityInfo(int stackId, int supportedRatBitMask,
                int voiceCap, int dataCap) {
            mStackId = stackId;
            mSupportedRatBitMask = supportedRatBitMask;
            mVoiceDataCap = voiceCap;
            mMaxDataCap = dataCap;
        }

        public int getSupportedRatBitMask() { return mSupportedRatBitMask;}

        public int getStackId() { return mStackId;}
        public int getMaxDataCap() { return mMaxDataCap;}

        public String toString() {
            return "[stack = " + mStackId + ", SuppRatBitMask = "
                + mSupportedRatBitMask + ", voiceDataCap = "
                + mVoiceDataCap + ", maxDataCap = "
                + mMaxDataCap +"]";
        }
    };

    //***** Events
    private static final int CMD_DEACTIVATE_ALL_SUBS = 1;
    private static final int EVENT_GET_MODEM_CAPS_DONE = 2;
    private static final int CMD_TRIGGER_UNBIND = 3;
    private static final int EVENT_UNBIND_DONE = 4;
    private static final int CMD_TRIGGER_BIND = 5;
    private static final int EVENT_BIND_DONE = 6;
    private static final int EVENT_SET_PREF_MODE_DONE = 7;
    private  static final int EVENT_SUB_DEACTIVATED = 8;
    private static final int EVENT_RADIO_AVAILABLE = 9;
    private static final int EVENT_MODEM_CAPABILITY_CHANGED = 10;

    //*****States
    private static final int STATE_UNKNOWN = 1;
    private static final int STATE_GOT_MODEM_CAPS = 2;
    private static final int STATE_SUB_DEACT = 3;
    private static final int STATE_UNBIND = 4;
    private static final int STATE_BIND = 5;
    private static final int STATE_SUB_ACT = 6;
    private static final int STATE_SET_PREF_MODE = 7;

    //*****Constants
    private static final int BIND_TO_STACK = 1;
    private static final int UNBIND_TO_STACK = 0;
    private static final int GET_MODEM_CAPS_BUFFER_LEN = 7;
    private static final int SUCCESS = 1;
    private static final int FAILURE = 0;
    private static final int PRIMARY_STACK_ID = 0;
    private static final int DEFAULT_MAX_DATA_ALLOWED = 1;

    //***** Class Variables
    private static ModemStackController sModemStackController;
    private CommandsInterface[] mCi;
    private Context mContext;
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private int mActiveSubCount = 0;
    private int mDeactivedSubCount = 0;
    private int[] mPreferredStackId = new int[mNumPhones];
    private int[] mCurrentStackId = new int[mNumPhones];
    private int[] mPrefNwMode = new int[mNumPhones];
    private int[] mSubState = new int[mNumPhones];
    private boolean mIsStackReady = false;
    private boolean mIsRecoveryInProgress = false;
    private boolean mIsPhoneInEcbmMode = false;
    private boolean mModemRatCapabilitiesAvailable = false;
    private boolean mDeactivationInProgress = false;
    private boolean[] mCmdFailed = new boolean[mNumPhones];
    private RegistrantList mStackReadyRegistrants = new RegistrantList();
    private RegistrantList mModemRatCapsAvailableRegistrants = new RegistrantList();
    private RegistrantList mModemDataCapsAvailableRegistrants = new RegistrantList();
    private Message mUpdateStackMsg;
    private HashMap<Integer, Integer> mSubcriptionStatus = new HashMap<Integer, Integer>();

    //Modem capabilities as per StackId
    private ModemCapabilityInfo[] mModemCapInfo = null;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED.
                    equals(intent.getAction())) {
                if (intent.getBooleanExtra(PhoneConstants.PHONE_IN_ECM_STATE, false)) {
                    logd("Device is in ECBM Mode");
                    mIsPhoneInEcbmMode = true;
                } else {
                    logd("Device is out of ECBM Mode");
                    mIsPhoneInEcbmMode = false;
                }
            } else if (TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE.equals(intent.getAction())) {
                long subId = intent.getLongExtra(SubscriptionManager._ID,
                        SubscriptionManager.INVALID_SUB_ID);
                String column = intent.getStringExtra(TelephonyIntents.EXTRA_COLUMN_NAME);
                int intValue = intent.getIntExtra(TelephonyIntents.EXTRA_INT_CONTENT, 0);
                logd("Received ACTION_SUBINFO_CONTENT_CHANGE on subId: " + subId
                        + "for " + column + " intValue: " + intValue);

                if (mDeactivationInProgress && column != null
                        && column.equals(SubscriptionManager.SUB_STATE)) {
                        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
                    if (intValue == SubscriptionManager.INACTIVE &&
                            mSubcriptionStatus.get(phoneId)== SubscriptionManager.ACTIVE) {

                        //deactivated the activated sub
                        Message msg = obtainMessage(EVENT_SUB_DEACTIVATED, new Integer(phoneId));
                        AsyncResult.forMessage(msg, SubscriptionStatus.SUB_DEACTIVATED, null);
                        sendMessage(msg);
                    }
                }
            } else if (TelephonyIntents.ACTION_SUBSCRIPTION_SET_UICC_RESULT.
                    equals(intent.getAction())) {
                long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUB_ID);
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        PhoneConstants.PHONE_ID1);
                int status = intent.getIntExtra(TelephonyIntents.EXTRA_RESULT,
                        PhoneConstants.FAILURE);
                logd("Received ACTION_SUBSCRIPTION_SET_UICC_RESULT on subId: " + subId
                        + "phoneId " + phoneId + " status: " + status);
                if (mDeactivationInProgress && (status == PhoneConstants.FAILURE)) {
                    // Sub deactivation failed
                    Message msg = obtainMessage(EVENT_SUB_DEACTIVATED, new Integer(phoneId));
                    AsyncResult.forMessage(msg, SubscriptionStatus.SUB_ACTIVATED, null);
                    sendMessage(msg);
                }
            }
        }};


    //***** Class Methods
    public static ModemStackController make(Context context, UiccController uiccMgr,
            CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "getInstance");
        if (sModemStackController == null) {
            sModemStackController = new ModemStackController(context, uiccMgr, ci);
        } else {
            throw new RuntimeException("ModemStackController.make() should only be called once");
        }
        return sModemStackController;
    }

    public static ModemStackController getInstance() {
        if (sModemStackController == null) {
            throw new RuntimeException("ModemStackController.getInstance called before make()");
        }
        return sModemStackController;
    }

    //***** Constructor
    private ModemStackController(Context context, UiccController uiccManager,
            CommandsInterface[] ci) {
        logd("Constructor - Enter");

        mCi = ci;
        mContext = context;
        mModemCapInfo = new ModemCapabilityInfo[mNumPhones];

        for (int i = 0; i < mCi.length; i++) {
            mCi[i].registerForAvailable(this, EVENT_RADIO_AVAILABLE, new Integer(i));
            mCi[i].registerForModemCapEvent(this, EVENT_MODEM_CAPABILITY_CHANGED, null);
        }

        for (int i = 0; i < mNumPhones; i++) {
            mPreferredStackId[i] = i;
            mCurrentStackId[i] = i;
            mSubState[i] = STATE_UNKNOWN;
            mCmdFailed[i] = false;
        }

        // In case of Single Sim, Stack is by default ready
        if (mNumPhones == 1) mIsStackReady = true;

        IntentFilter filter =
                new IntentFilter(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        filter.addAction(TelephonyIntents.ACTION_SUBSCRIPTION_SET_UICC_RESULT);
        mContext.registerReceiver(mReceiver, filter);
        logd("Constructor - Exit");
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        Integer phoneId;

        switch(msg.what) {
            case EVENT_RADIO_AVAILABLE:
                ar = (AsyncResult)msg.obj;
                phoneId = (Integer)ar.userObj;
                logd("EVENT_RADIO_AVAILABLE");
                processRadioAvailable(ar, phoneId);
                break;

            case EVENT_GET_MODEM_CAPS_DONE:
                ar = (AsyncResult)msg.obj;
                phoneId = (Integer)ar.userObj;
                logd("EVENT_GET_MODEM_CAPS_DONE");
                onGetModemCapabilityDone(ar, (byte[])ar.result, phoneId);
                break;

            case EVENT_MODEM_CAPABILITY_CHANGED:
                ar = (AsyncResult)msg.obj;
                logd("EVENT_MODEM_CAPABILITY_CHANGED ar =" + ar);
                onUnsolModemCapabilityChanged(ar);
                break;

            case CMD_DEACTIVATE_ALL_SUBS:
                logd("CMD_DEACTIVATE_ALL_SUBS");
                deactivateAllSubscriptions();
                break;

            case EVENT_SUB_DEACTIVATED:
                ar = (AsyncResult)msg.obj;
                phoneId = (Integer)ar.userObj;
                logd("EVENT_SUB_DEACTIVATED");
                onSubDeactivated(ar, phoneId);
                break;

            case CMD_TRIGGER_UNBIND:
                phoneId = (Integer)msg.obj;
                logd("CMD_TRIGGER_UNBIND");
                unbindStackOnSub(phoneId);
                break;

            case EVENT_UNBIND_DONE:
                ar = (AsyncResult)msg.obj;
                phoneId = (Integer)ar.userObj;
                logd("EVENT_UNBIND_DONE");
                onUnbindComplete(ar, phoneId);
                break;

            case CMD_TRIGGER_BIND:
                phoneId = (Integer)msg.obj;
                logd("CMD_TRIGGER_BIND");
                bindStackOnSub(phoneId);
                break;

            case EVENT_BIND_DONE:
                ar = (AsyncResult)msg.obj;
                phoneId = (Integer)ar.userObj;
                logd("EVENT_BIND_DONE");
                onBindComplete(ar, phoneId);
                break;

            case EVENT_SET_PREF_MODE_DONE:
                ar = (AsyncResult)msg.obj;
                phoneId = (Integer)ar.userObj;
                logd("EVENT_SET_PREF_MODE_DONE");
                onSetPrefNwModeDone(ar, phoneId);
                break;

            default:
                break;
        }

    }

    private void processRadioAvailable(AsyncResult ar, int phoneId) {
        logd("processRadioAvailable on phoneId = " + phoneId);

        if (phoneId >= 0 && phoneId < mNumPhones) {
            Message getModemCapsMsg = Message.obtain(this, EVENT_GET_MODEM_CAPS_DONE,
                    new Integer(phoneId));

            mCi[phoneId].getModemCapability(getModemCapsMsg);

        } else {
            loge("Invalid Index!!!");
        }
    }

    private void onGetModemCapabilityDone(AsyncResult ar, byte[] result, int phoneId) {

        if (result == null && ar.exception instanceof CommandException) {
            loge("onGetModemCapabilityDone: EXIT!, result null or Exception =" + ar.exception);
            //On Modem Packages which do not support GetModemCaps RIl will return exception
            //On such Modem packages notify stack is ready so that SUB Activation can continue.
            mIsStackReady = true;
            mStackReadyRegistrants.notifyRegistrants();
            return;
        }

        logd("onGetModemCapabilityDone on phoneId[" + phoneId + "] result = " + result);

        if (phoneId >= 0 && phoneId < mNumPhones) {
            //Parse response and store it.
            mSubState[phoneId] = STATE_GOT_MODEM_CAPS;
            parseGetModemCapabilityResponse(result, phoneId);

            //Wait till we get Modem Capabilities on all subs
            if (areAllSubsinSameState(STATE_GOT_MODEM_CAPS)) {
                notifyModemRatCapabilitiesAvailable();
            }
        } else {
            loge("Invalid Index!!!");
        }
    }

    private void onUnsolModemCapabilityChanged(AsyncResult ar) {
        logd("onUnsolModemCapabilityChanged");
        RIL.UnsolOemHookBuffer unsolOemHookBuffer = (RIL.UnsolOemHookBuffer)ar.result;

        if(unsolOemHookBuffer == null && ar.exception instanceof CommandException) {
            loge("onUnsolModemCapabilityChanged: EXIT!, result null or Exception =" + ar.exception);
            return;

        }
        byte[] data = (byte[])unsolOemHookBuffer.getUnsolOemHookBuffer();
        int phoneId = unsolOemHookBuffer.getRilInstance();

        logd("onUnsolModemCapabilityChanged on phoneId = " + phoneId);

        parseGetModemCapabilityResponse(data, phoneId);
        notifyModemDataCapabilitiesAvailable();

    }

    private void onSubDeactivated(AsyncResult ar, int phoneId) {
        SubscriptionStatus subStatus = (SubscriptionStatus)ar.result;
        if (subStatus == null ||
                (subStatus != null && SubscriptionStatus.SUB_DEACTIVATED != subStatus)) {
            loge("onSubDeactivated on phoneId[" + phoneId + "] Failed!!!");
            mCmdFailed[phoneId] = true;
        }

        logd("onSubDeactivated on phoneId[" + phoneId + "] subStatus = " + subStatus);

        //avoid duplicate entries
        if (mSubState[phoneId] == STATE_SUB_DEACT) return;

        mSubState[phoneId] = STATE_SUB_DEACT;
        mDeactivedSubCount++;

        //Wait till we get Sub Deact response on all active subs
        if (mDeactivedSubCount == mActiveSubCount) {
            //if any deact failed notify registrants to activate any deactivated subs
            //and stop binding process. No need to recover here.
            if (isAnyCmdFailed()) {
                mIsRecoveryInProgress = false;
                mIsStackReady = true;
                mStackReadyRegistrants.notifyRegistrants();
            } else {
                mDeactivationInProgress = false;
                triggerUnBindingOnAllSubs();
            }
        }
    }

    private void bindStackOnSub(int phoneId) {
        logd("bindStack " + mPreferredStackId[phoneId] + " On phoneId[" + phoneId + "]");
        Message msg = Message.obtain(this, EVENT_BIND_DONE, new Integer(phoneId));
        mCi[phoneId].updateStackBinding(mPreferredStackId[phoneId], BIND_TO_STACK, msg);
    }

    private void unbindStackOnSub(int phoneId) {
        logd("unbindStack " + mCurrentStackId[phoneId] + " On phoneId[" + phoneId + "]");
        Message msg = Message.obtain(this, EVENT_UNBIND_DONE, new Integer(phoneId));
        mCi[phoneId].updateStackBinding(mCurrentStackId[phoneId], UNBIND_TO_STACK, msg);
    }

    private void onUnbindComplete(AsyncResult ar, int phoneId) {
        if (ar.exception instanceof CommandException) {
            mCmdFailed[phoneId] = true;
            loge("onUnbindComplete(" + phoneId + "): got Exception =" + ar.exception);
        }

        mSubState[phoneId] = STATE_UNBIND;

        //Wait till we get UNBIND response on all subs
        if (areAllSubsinSameState(STATE_UNBIND)) {
            if (isAnyCmdFailed()) {
                recoverToPrevState();
                return;
            }
            triggerBindingOnAllSubs();
        }
    }

    private void onBindComplete(AsyncResult ar, int phoneId) {
        if (ar.exception instanceof CommandException) {
            mCmdFailed[phoneId] = true;
            loge("onBindComplete(" + phoneId + "): got Exception =" + ar.exception);
        }

        mSubState[phoneId] = STATE_BIND;

        //Wait till we get BIND response on all subs
        if (areAllSubsinSameState(STATE_BIND)) {
            if (isAnyCmdFailed()) {
                recoverToPrevState();
                return;
            }
            setPrefNwTypeOnAllSubs();
        }
    }

    private void onSetPrefNwModeDone(AsyncResult ar, int phoneId) {
        if (ar.exception instanceof CommandException) {
            mCmdFailed[phoneId] = true;
            loge("onSetPrefNwModeDone(SUB:" + phoneId + "): got Exception =" + ar.exception);
        }

        mSubState[phoneId] = STATE_SET_PREF_MODE;

        //Wait till we get Set Pref NwMode response on all subs
        if (areAllSubsinSameState(STATE_SET_PREF_MODE)) {
            if (isAnyCmdFailed()) {
                recoverToPrevState();
                return;
            }

            if (mUpdateStackMsg != null) {
                sendResponseToTarget(mUpdateStackMsg, RILConstants.SUCCESS);
                mUpdateStackMsg = null;
            }
            updateNetworkSelectionMode();
            notifyStackReady();
        }
    }

    private void updateNetworkSelectionMode() {
        for (int i = 0; i < mNumPhones; i++) {
            mCi[i].setNetworkSelectionModeAutomatic(null);
        }
    }

    private void triggerUnBindingOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < mNumPhones; i++) {
            sendMessage(obtainMessage(CMD_TRIGGER_UNBIND, new Integer(i)));
        }
    }

    private void triggerBindingOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < mNumPhones; i++) {
            sendMessage(obtainMessage(CMD_TRIGGER_BIND, new Integer(i)));
        }
    }

    private void triggerDeactivationOnAllSubs() {
        resetSubStates();
        sendMessage(obtainMessage(CMD_DEACTIVATE_ALL_SUBS));
    }


    private void setPrefNwTypeOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < mNumPhones; i++) {
            Message resp = obtainMessage(EVENT_SET_PREF_MODE_DONE, new Integer(i));
            mCi[i].setPreferredNetworkType(mPrefNwMode[i], resp);
        }
    }

    private boolean areAllSubsinSameState(int state) {
        for (int subState : mSubState) {
            logd("areAllSubsinSameState state= "+state + " substate="+subState);
            if (subState != state) return false;
        }
        return true;
    }

    private void resetSubStates() {
        for (int i = 0; i < mNumPhones; i++) {
            mSubState[i] = STATE_UNKNOWN;
            mCmdFailed[i] = false;
        }
    }

    private boolean isAnyCmdFailed() {
        boolean result = false;
        for (int i = 0; i < mNumPhones; i++) {
            if (mCmdFailed[i] != false) {
                result = true;
            }
        }
        return result;
    }

    private void updateModemCapInfo(int phoneId, int stackId, int supportedRatBitMask,
            int voiceDataCap, int maxDataCap) {

        mCurrentStackId[phoneId] = stackId;
        //Modem Capabilities are stored per StackId.
        mModemCapInfo[mCurrentStackId[phoneId]] = new ModemCapabilityInfo
                (mCurrentStackId[phoneId], supportedRatBitMask, voiceDataCap , maxDataCap);
        logd("updateModemCapInfo: ModemCaps[" + phoneId +
                "]" + mModemCapInfo[mCurrentStackId[phoneId]]);


    }

    private void parseGetModemCapabilityResponse(byte[] result, int phoneId) {
        int supportedRatBitMask;
        int stackId;
        int voiceDataCap;
        int maxDataCap;

        if (result.length != GET_MODEM_CAPS_BUFFER_LEN) {
            loge("parseGetModemCapabilityResponse: EXIT!, result length(" + result.length
                    + ") and Expected length(" + GET_MODEM_CAPS_BUFFER_LEN + ") not matching.");
            return;
        }
        logd("parseGetModemCapabilityResponse: buffer = "+IccUtils.bytesToHexString(result));
        ByteBuffer respBuffer = ByteBuffer.wrap(result);
        respBuffer.order(ByteOrder.nativeOrder());

        stackId = respBuffer.get();

        if ( !(stackId >= 0 && stackId < mNumPhones) ) {
            loge("Invalid Index!!!");
            return;
        }
        supportedRatBitMask = respBuffer.getInt();
        voiceDataCap = respBuffer.get();
        maxDataCap = respBuffer.get();

        updateModemCapInfo(phoneId, stackId, supportedRatBitMask, voiceDataCap, maxDataCap);

    }

    private void syncPreferredNwModeFromDB() {
        for (int i = 0; i < mNumPhones; i++) {
            try {
                mPrefNwMode[i] = TelephonyManager.getIntAtIndex(mContext.getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE, i);
            } catch (SettingNotFoundException snfe) {
                loge("getPreferredNetworkMode: Could not find PREFERRED_NETWORK_MODE!!!");
                mPrefNwMode[i] = Phone.PREFERRED_NT_MODE;
            }
        }
    }

    private boolean isAnyCallsInProgress() {
        boolean isCallInProgress = false;
        for (int i = 0; i < mNumPhones; i++) {
            if (TelephonyManager.getDefault().getCallState(i)
                    != TelephonyManager.CALL_STATE_IDLE) {
                isCallInProgress = true;
                break;
            }
        }
        return isCallInProgress;
    }

    public boolean isStackReady() {
        return mIsStackReady;
    }

    public int getMaxDataAllowed() {
        logd("getMaxDataAllowed");
        int ret = DEFAULT_MAX_DATA_ALLOWED;
        List<Integer> unsortedList = new ArrayList<Integer>();

        for (int i = 0; i < mNumPhones; i++) {
            if (mModemCapInfo[i] != null) {
                unsortedList.add(mModemCapInfo[i].getMaxDataCap());
            }
        }
        Collections.sort(unsortedList);
        int listSize = unsortedList.size();
        if (listSize > 0) {
            ret = unsortedList.get(listSize - 1);
        }
        return ret;
    }

    public int getCurrentStackIdForPhoneId(int phoneId) {
        return mCurrentStackId[phoneId];
    }

    public int getPrimarySub() {
        for (int i = 0; i < mNumPhones; i++) {
            if (getCurrentStackIdForPhoneId(i) == PRIMARY_STACK_ID) {
                return i;
            }
        }
        return 0;
    }

    public ModemCapabilityInfo getModemRatCapsForPhoneId(int phoneId) {
        return mModemCapInfo[mCurrentStackId[phoneId]];
    }

    public int updateStackBinding(int[] prefStackIds, boolean isBootUp, Message msg) {
        boolean isUpdateRequired = false;
        boolean callInProgress = isAnyCallsInProgress();

        if (mNumPhones == 1) {
            loge("No need to update Stack Binding in case of Single Sim.");
            return FAILURE;
        }

        if (callInProgress || mIsPhoneInEcbmMode || (!mIsStackReady && !isBootUp)) {
            loge("updateStackBinding: Calls is progress = " + callInProgress +
                    ", mIsPhoneInEcbmMode = " + mIsPhoneInEcbmMode + ", mIsStackReady = "
                    + mIsStackReady + ". So EXITING!!!");
            return FAILURE;
        }
        for (int i = 0; i < mNumPhones; i++) {
            mPreferredStackId[i] = prefStackIds[i];
        }

        for (int i = 0; i < mNumPhones; i++) {
            if (mPreferredStackId[i] != mCurrentStackId[i]) {
                //if preferred stackId is different from current, bindupdate is required.
                isUpdateRequired = true;
                break;
            }
        }

        if (isUpdateRequired) {
            mIsStackReady = false;
            //Store the msg object , so that result of updateStackbinding can be sent later.
            mUpdateStackMsg = msg;
            //Get Stored prefNwMode for all the subs and send request to RIL after update binding.
            syncPreferredNwModeFromDB();
            if (isBootUp) {
                triggerUnBindingOnAllSubs();
            } else {
                triggerDeactivationOnAllSubs();
            }
        } else {
            //incase of bootup if cross binding is not required send stack ready notification.
            if (isBootUp) notifyStackReady();
            return FAILURE;
        }
        return SUCCESS;
    }

    private void deactivateAllSubscriptions() {
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        List<SubInfoRecord> subInfoList = subCtrlr.getActiveSubInfoList();
        mActiveSubCount = 0;
        for (SubInfoRecord subInfo : subInfoList) {
            int subStatus = subCtrlr.getSubState(subInfo.subId);
            if (subStatus == SubscriptionManager.ACTIVE) {
                mActiveSubCount++;
                subCtrlr.deactivateSubId(subInfo.subId);
            }
            mSubcriptionStatus.put(subInfo.slotId, subStatus);
        }
        if (mActiveSubCount > 0) {
            mDeactivedSubCount = 0;
            mDeactivationInProgress = true;
        } else {
            mDeactivationInProgress = false;
            triggerUnBindingOnAllSubs();
        }
    }

    private void notifyStackReady() {
        logd("notifyStackReady: Stack is READY!!!");
        mIsRecoveryInProgress = false;
        mIsStackReady = true;
        resetSubStates();

        for (int i = 0; i < mNumPhones; i++) {
            //update the current stackIds
            mCurrentStackId[i] = mPreferredStackId[i];
        }

        //notify binding completed to all StackReady registrants.
        //including subscriptionManager which activates available subs on binding complete.
        mStackReadyRegistrants.notifyRegistrants();
    }

    public void registerForStackReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        if (mIsStackReady) {
            r.notifyRegistrant();
        }
        synchronized (mStackReadyRegistrants) {
            mStackReadyRegistrants.add(r);
        }
    }

    private void notifyModemRatCapabilitiesAvailable() {
           logd("notifyGetRatCapabilitiesDone: Got RAT capabilities for all Stacks!!!");
           mModemRatCapabilitiesAvailable = true;
           mModemRatCapsAvailableRegistrants.notifyRegistrants();
    }

    private void notifyModemDataCapabilitiesAvailable() {
           logd("notifyGetDataCapabilitiesDone");
           mModemDataCapsAvailableRegistrants.notifyRegistrants();
    }

    public void registerForModemRatCapsAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        if (mModemRatCapabilitiesAvailable) {
            r.notifyRegistrant();
        }
        synchronized (mModemRatCapsAvailableRegistrants) {
            mModemRatCapsAvailableRegistrants.add(r);
        }
    }

    public void registerForModemDataCapsUpdate(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mModemDataCapsAvailableRegistrants) {
            mModemDataCapsAvailableRegistrants.add(r);
        }
    }

    private void recoverToPrevState() {
        if (mIsRecoveryInProgress) {
            //Already in recovery process and exception occured again.
            //Get stored msg if available and send exception and stop recovery.
            if (mUpdateStackMsg != null) {
                sendResponseToTarget(mUpdateStackMsg, RILConstants.GENERIC_FAILURE);
                mUpdateStackMsg = null;
            }
            mIsRecoveryInProgress = false;
            if(STATE_SET_PREF_MODE == mSubState[0]) {
                //Already recovery in progress, got failure in SetPrefNwmode. We are bailing out.
                //As Set Pref is failed, Binding is completed. so update and notify same.
                notifyStackReady();
            }
            return;
        }

        mIsRecoveryInProgress = true;
        //Binding to Preferred Stack failed, recovery mode: fallback to Current stackIds
        for (int i = 0; i < mNumPhones; i++) {
            mPreferredStackId[i] = mCurrentStackId[i];
        }
        triggerUnBindingOnAllSubs();
    }

    private void sendResponseToTarget(Message response, int responseCode) {
        Exception e = CommandException.fromRilErrno(responseCode);
        AsyncResult.forMessage(response, null, e);
        response.sendToTarget();
    }

    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    private void loge(String string) {
        Rlog.e(LOG_TAG, string);
    }
}
