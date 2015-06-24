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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.uicc.UiccController;

import com.android.internal.telephony.ModemStackController.ModemCapabilityInfo;

import java.util.HashMap;

/*
 * ModemBindingPolicyHandler: Class used to determine stack binding policy based on
 * Network mode selected by the user.
 *
 * Before sending NwMode to RIL, checks if the NwMode is supported on the Stack and
 * if possible initiate a cross mapping sequence.
 * Includes:
 *    Current preferred NwModes
 *    Logic for determining NwMode supported on particular Stack
 *    Logic for determining stack binding policy based on Network mode
 *    selected by user.
 *    Send request for Stack binding update based on above policy.
 */

public class ModemBindingPolicyHandler extends Handler {
    static final String LOG_TAG = "ModemBindingPolicyHandler";

    //*****Network Mode bit vector mask
    //Basic Network Modes
    private static final int NETWORK_MASK_GSM_ONLY =
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_GSM |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_GPRS |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_EDGE;
    private static final int NETWORK_MASK_WCDMA_ONLY =
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_UMTS |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_HSPA |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP;
    private static final int NETWORK_MASK_CDMA_NO_EVDO =
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_IS95A |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_IS95B |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT;
    private static final int NETWORK_MASK_EVDO_NO_CDMA =
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0 |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B |
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD;
    private static final int NETWORK_MASK_LTE_ONLY =
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
    private static final int NETWORK_MASK_TD_SCDMA_ONLY =
            1 << ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA;

    //Complex modes, i.e. Combinations of above basic Network Modes
    private static final int NETWORK_MASK_GSM_UMTS =
            NETWORK_MASK_GSM_ONLY |
            NETWORK_MASK_WCDMA_ONLY;
    private static final int NETWORK_MASK_CDMA =
            NETWORK_MASK_CDMA_NO_EVDO |
            NETWORK_MASK_EVDO_NO_CDMA;
    private static final int NETWORK_MASK_WCDMA_PREF =
            NETWORK_MASK_GSM_UMTS;
    private static final int NETWORK_MASK_GLOBAL =
            NETWORK_MASK_GSM_UMTS |
            NETWORK_MASK_CDMA;
    private static final int NETWORK_MASK_LTE_CDMA_EVDO =
            NETWORK_MASK_LTE_ONLY |
            NETWORK_MASK_CDMA;
    private static final int NETWORK_MASK_LTE_GSM_WCDMA =
            NETWORK_MASK_LTE_ONLY |
            NETWORK_MASK_GSM_UMTS;
    private static final int NETWORK_MASK_LTE_CMDA_EVDO_GSM_WCDMA =
            NETWORK_MASK_LTE_ONLY |
            NETWORK_MASK_CDMA |
            NETWORK_MASK_GSM_UMTS;
    private static final int NETWORK_MASK_LTE_WCDMA =
            NETWORK_MASK_LTE_ONLY |
            NETWORK_MASK_WCDMA_ONLY;
    private static final int NETWORK_MASK_TD_SCDMA_WCDMA =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_WCDMA_ONLY;
    private static final int NETWORK_MASK_TD_SCDMA_LTE =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_LTE_ONLY;
    private static final int NETWORK_MASK_TD_SCDMA_GSM =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_GSM_ONLY;
    private static final int NETWORK_MASK_TD_SCDMA_GSM_LTE =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_GSM_ONLY |
            NETWORK_MASK_LTE_ONLY;
    private static final int NETWORK_MASK_TD_SCDMA_GSM_WCDMA =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_GSM_UMTS;
    private static final int NETWORK_MASK_TD_SCDMA_WCDMA_LTE =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_LTE_WCDMA;
    private static final int NETWORK_MASK_TD_SCDMA_GSM_WCDMA_LTE =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_LTE_GSM_WCDMA;
    private static final int NETWORK_MASK_TD_SCDMA_CDMA_EVDO_GSM_WCDMA =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_CDMA |
            NETWORK_MASK_GSM_UMTS;
    private static final int NETWORK_MASK_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA =
            NETWORK_MASK_TD_SCDMA_ONLY |
            NETWORK_MASK_LTE_CMDA_EVDO_GSM_WCDMA;

    //***** Events
    private static final int EVENT_MODEM_RAT_CAPS_AVAILABLE = 1;
    private static final int EVENT_UPDATE_BINDING_DONE = 2;
    private static final int EVENT_SET_NW_MODE_DONE = 3;
    private static final int EVENT_SET_NW_MODE_FROM_DB_DONE = 4;
    private static final int EVENT_GET_NW_MODE_DONE = 5;

    //*****Constants
    private static final int SUCCESS = 1;
    private static final int FAILURE = 0;

    //***** Class Variables
    private static ModemBindingPolicyHandler sModemBindingPolicyHandler;
    private static ModemStackController mModemStackController;
    private CommandsInterface[] mCi;
    private Context mContext;
    private int mNumOfSetPrefNwModeSuccess = 0;
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private boolean mModemRatCapabilitiesAvailable = false;
    private boolean mIsSetPrefNwModeInProgress = false;
    //private boolean mIsBindingInProgress = false;
    private boolean mIsSetDsdsNwModeInProgress = false;
    private int[] mPreferredStackId = new int[mNumPhones];
    private int[] mCurrentStackId = new int[mNumPhones];
    private int[] mPrefNwMode = new int[mNumPhones];
    private int[] mNwModeinSubIdTable = new int[mNumPhones];
    private HashMap<Integer, Message> mStoredResponse = new HashMap<Integer, Message>();

    //Modem capabilities as per StackId
    private ModemCapabilityInfo[] mModemCapInfo = null;

    //***** Class Methods
    public static ModemBindingPolicyHandler make(Context context, UiccController uiccMgr,
            CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "getInstance");
        if (sModemBindingPolicyHandler == null) {
            sModemBindingPolicyHandler = new ModemBindingPolicyHandler(context, uiccMgr, ci);
        } else {
            throw new RuntimeException("ModemBindingPolicyHandler.make() should be called once");
        }
        return sModemBindingPolicyHandler;
    }

    public static ModemBindingPolicyHandler getInstance() {
        if (sModemBindingPolicyHandler == null) {
            throw new RuntimeException("ModemBindingPolicyHdlr.getInstance called before make()");
        }
        return sModemBindingPolicyHandler;
    }

    //***** Constructor
    private ModemBindingPolicyHandler(Context context, UiccController uiccManager,
            CommandsInterface[] ci) {
        logd("Constructor - Enter");

        mCi = ci;
        mContext = context;
        mModemStackController = ModemStackController.getInstance();
        mModemCapInfo = new ModemCapabilityInfo[mNumPhones];

        mModemStackController.registerForModemRatCapsAvailable
                (this, EVENT_MODEM_RAT_CAPS_AVAILABLE, null);

        for (int i = 0; i < mNumPhones; i++) {
            mPreferredStackId[i] = i;
            mCurrentStackId[i] = i;
            mStoredResponse.put(i, null);
        }

        logd("Constructor - Exit");
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch(msg.what) {
            case EVENT_UPDATE_BINDING_DONE:
                ar = (AsyncResult)msg.obj;
                handleUpdateBindingDone(ar);
                break;

            case EVENT_MODEM_RAT_CAPS_AVAILABLE:
                handleModemRatCapsAvailable();
                break;

            case EVENT_SET_NW_MODE_DONE:
                handleSetPreferredNetwork(msg);
                break;

            case EVENT_SET_NW_MODE_FROM_DB_DONE:
                ar = (AsyncResult)msg.obj;
                int phoneId = msg.arg1;
                int networkMode = msg.arg2;
                if (ar.exception != null) {
                    logd("Failed to set preferred network mode for slot" + phoneId);
                }
                Message msg2 = obtainMessage(EVENT_GET_NW_MODE_DONE, phoneId, networkMode);
                mCi[phoneId].getPreferredNetworkType(msg2);
                break;
            case EVENT_GET_NW_MODE_DONE:
                handleGetPreferredNetwork(msg);
                break;

            default:
                break;
        }
    }

    private void handleSetPreferredNetwork(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        int index = (Integer) ar.userObj;
        if (ar.exception == null) {
            mNumOfSetPrefNwModeSuccess++;
            // set nw mode success for all the subs, then update value to DB
            if (mNumOfSetPrefNwModeSuccess == mNumPhones) {
                for (int i = 0; i < mNumPhones; i++) {
                    logd("Updating network mode in DB for slot[" + i + "] with "
                            + mNwModeinSubIdTable[i]);
                    TelephonyManager.putIntAtIndex(mContext.getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                            i, mNwModeinSubIdTable[i]);
                }
                mNumOfSetPrefNwModeSuccess = 0;
            }
        } else {
            logd("Failed to set preferred network mode for slot" + index);
            mNumOfSetPrefNwModeSuccess = 0;
        }

    }

    private void handleGetPreferredNetwork(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        int modemNetworkMode = ((int[]) ar.result)[0];
        int phoneId = msg.arg1;
        int networkMode = msg.arg2; // unused.
        if (ar.exception == null) {
            logd("Updating network mode in DB for slot[" + phoneId + "] with "
                    + modemNetworkMode);
            TelephonyManager.putIntAtIndex(mContext.getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                    phoneId, modemNetworkMode);
        } else {
            logd("Failed to get preferred network mode for slot" + phoneId);
        }

    }

    private void handleUpdateBindingDone(AsyncResult ar) {
        mIsSetPrefNwModeInProgress = false;

        for (int i = 0; i < mNumPhones; i++) {
            int errorCode = RILConstants.SUCCESS;
            Message resp = mStoredResponse.get(i);
            if (resp != null) {
                if (ar.exception != null) {
                    errorCode = RILConstants.GENERIC_FAILURE;
                }
                sendResponseToTarget(resp, errorCode);
                mStoredResponse.put(i, null);
            }
        }
    }

    /*
    * updatePrefNwTypeIfRequired: Method used to set pref network type if required.
    *
    * Description: If Network mode for a subid in simInfo table is valid and and is not
    * equal to value in DB, then update the DB value and send request to RIL.
    */
    public void updatePrefNwTypeIfRequired(){
        boolean updateRequired = false;
        syncPreferredNwModeFromDB();
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        for (int i=0; i < mNumPhones; i++ ) {
            int[] subIdList = subCtrlr.getSubId(i);
            if (subIdList != null && subIdList[0] > 0) {
                int subId = subIdList[0];
                mNwModeinSubIdTable[i] = subCtrlr.getNwMode(subId);
                if (mNwModeinSubIdTable[i] == SubscriptionManager.DEFAULT_NW_MODE){
                    updateRequired = false;
                    break;
                }
                if (mNwModeinSubIdTable[i] != mPrefNwMode[i]) {
                    updateRequired = true;
                }
            }
        }

        if (updateRequired) {
            if (FAILURE == updateStackBindingIfRequired(false)) {
                //In case of Update Stack Binding not required or failure, send setPrefNwType to
                //RIL immediately. In case of success after stack binding completed setPrefNwType
                //request is anyways sent.
                for (int i=0; i < mNumPhones; i++ ) {
                    Message msg = obtainMessage(EVENT_SET_NW_MODE_DONE, i);
                    mCi[i].setPreferredNetworkType(mNwModeinSubIdTable[i], msg);
                }
            }
       }
    }

    private void handleModemRatCapsAvailable() {
        mModemRatCapabilitiesAvailable = true;
        //Initialization sequence: Need to send Bind request always, so override is true.
        if (SUCCESS == updateStackBindingIfRequired(true)) mIsSetPrefNwModeInProgress = true;
    }

    private void syncCurrentStackInfo() {
        //Get Current phoneId to stack mapping.
        for (int i = 0; i < mNumPhones; i++) {
            mCurrentStackId[i] = mModemStackController.getCurrentStackIdForPhoneId(i);
            mModemCapInfo[mCurrentStackId[i]] = mModemStackController.getModemRatCapsForPhoneId(i);
            //reset Preferred to current.
            mPreferredStackId[i] = (mCurrentStackId[i] >= 0) ? mCurrentStackId[i] : i;
        }
    }

    /*
    * updateStackBindingIfRequired: Method used to update stack binding if required.
    * @param: boolean override - if true send update stack binding request always
    * @return: int SUCCESS or FAILURE. i.e. Request acepted or not.
    *   SUCCESS, if update stack binding request is sent and it returns SUCCESS
    *   FAILURE, if update stack binding request is not sent or
    *       if update stack binding request is sent and it returns FAILURE.
    */
    private int updateStackBindingIfRequired(boolean isBootUp) {
        boolean isUpdateStackBindingRequired = false;
        int response = FAILURE;

        updatePreferredStackIds();

        for (int i = 0; i < mNumPhones; i++) {
            if (mPreferredStackId[i] != mCurrentStackId[i]) {
                //if preferred stackId is different from current, bindupdate is required.
                isUpdateStackBindingRequired = true;
                break;
            }
        }
        if (isBootUp || isUpdateStackBindingRequired) {
            Message msg = Message.obtain(this, EVENT_UPDATE_BINDING_DONE, null);
            response = mModemStackController.updateStackBinding(mPreferredStackId, isBootUp, msg);
        }

        return response;
    }

    /*
    * updatePreferredStackIds: Method used to get isCrossBindingRequired and set the Preferred
    * StackIds as per Network Mode for phoneId.
    * @return: boolean true/false based on crossMapping is required.
    *
    * Description: used to determine the preferred stackIds based on Network Mode selected
    * and return if CrossBindingRequired.
    * Logic:
    * 1. For each phoneId, check if current stack supports the present Network Mode.
    * 2. If supported, continue to next phoneId. If stacks on all phoneId support their respective
    *     Network Mode then crossmapping is not required so preferredStackIds will be same as
    *     CurrentStackIds
    * 3. If stack of any of the phoneId is not supporting their Network Mode, then check if stack of
    *     other phoneIds supports present NetworkMode.
    *    a. If none of the stacks support the present Network Mode, cross mapping is not required
    *       and preferredStackIds will be same as CurrentStackIds.
    *    b. If some other stack supports, check if network mode on other phoneId is supported by
    *       stack after cross mapping.
    *           i. if supported cross mapping is required and set preferredStackIds accordingly.
    *           ii. if not supported cross mapping is not required and preferredStackIds will be
    *              same as CurrentStackIds.
    */
    private void updatePreferredStackIds() {
        if (!mModemRatCapabilitiesAvailable) {
            loge("updatePreferredStackIds: Modem Capabilites are not Available. Return!!");
            return;
        }

        //Get current prefNwMode and Stack info before updating pref Stack
        syncPreferredNwModeFromDB();
        syncCurrentStackInfo();

        for (int curPhoneId = 0; curPhoneId < mNumPhones; curPhoneId++) {
            //Continue if current stack supports Network Mode.
            if (isNwModeSupportedOnStack(mPrefNwMode[curPhoneId], mCurrentStackId[curPhoneId])) {
                logd("updatePreferredStackIds: current stack[" + mCurrentStackId[curPhoneId] +
                        "]supports NwMode[" + mPrefNwMode[curPhoneId] + "] on phoneId["
                        + curPhoneId + "]");
                continue;
            }

            //Current stack i do not support present Network Mode.
            //Check if any other stack supports the present Network Mode
            for (int otherPhoneId = 0; otherPhoneId < mNumPhones; otherPhoneId++) {
                //continue for same stack, check only on other stacks
                if (otherPhoneId == curPhoneId) continue;

                if (isNwModeSupportedOnStack(mPrefNwMode[curPhoneId],
                        mCurrentStackId[otherPhoneId])) {
                    //Some other stack supports present Network Mode, Check if Network Mode
                    //on other PhoneId is supported on current Stack after Cross binding.
                    if (isNwModeSupportedOnStack(mPrefNwMode[otherPhoneId],
                            mCurrentStackId[curPhoneId])) {
                        logd("updatePreferredStackIds: Cross Binding is possible between phoneId["
                                + curPhoneId + "] and phoneId[" + otherPhoneId + "]");
                        //set preferred stackid of curPhoneId as current stack id of otherPhoneId
                        //and preferred stackid of otherPhoneId as current stack id of curPhoneId
                        mPreferredStackId[curPhoneId] = mCurrentStackId[otherPhoneId];
                        mPreferredStackId[otherPhoneId] = mCurrentStackId[curPhoneId];
                    }
                }
            }
        }
    }

    private boolean isNwModeSupportedOnStack(int nwMode, int stackId) {
        int[] numRatSupported = new int[mNumPhones];
        int maxNumRatSupported = 0;
        boolean isSupported = false;

        //Get num of RATs supported for this NwMode on all Stacks
        for (int i = 0; i < mNumPhones; i++) {
            numRatSupported[i] = getNumOfRatSupportedForNwMode(nwMode, mModemCapInfo[i]);
            if (maxNumRatSupported < numRatSupported[i]) maxNumRatSupported = numRatSupported[i];
        }

        //if current stackId supports Max RATs of all other stacks, then return true.
        if (numRatSupported[stackId] == maxNumRatSupported) isSupported = true;

        logd("nwMode:" + nwMode + ", on stack:" + stackId + " is " +
                (isSupported ? "Supported" : "Not Supported"));

        return isSupported;
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

    public void setPreferredNetworkType(int networkType, int phoneId,
            Message response) {
        //if binding is in progress return failure for this request
        if (mIsSetPrefNwModeInProgress) {
            loge("setPreferredNetworkType: In Progress:");
            sendResponseToTarget(response, RILConstants.GENERIC_FAILURE);
            return;
        }

        logd("setPreferredNetworkType: nwMode:" + networkType + ", on phoneId:" + phoneId);

        mIsSetPrefNwModeInProgress = true;

        //If CrossBinding request is not accepted, i.e. return value is FAILURE
        //send request directly to RIL, or else store the setpref Msg for later processing.
        if (updateStackBindingIfRequired(false) == SUCCESS) {
            mStoredResponse.put(phoneId, response);
        } else {
            mCi[phoneId].setPreferredNetworkType(networkType, response);
            mIsSetPrefNwModeInProgress = false;
        }
    }

    public void setPreferredNetworkTypesFromDB() {
        // If binding is in progress return failure for this request
        if (mIsSetPrefNwModeInProgress) {
            loge("setPreferredNetworkTypesFromDB: In Progress:");
            return;
        }

        syncPreferredNwModeFromDB();

        for (int i = 0; i < mNumPhones; i++) {
            logd("setPreferredNetworkTypesFromDB: nwMode:" + mPrefNwMode[i] + ", on phoneId:" + i);
        }

        mIsSetPrefNwModeInProgress = true;


        // If CrossBinding request is not accepted, i.e. return value is FAILURE
        // send request directly to RIL, or else store the setpref Msg for later processing.
        if (updateStackBindingIfRequired(false) == SUCCESS) {
            for (int i = 0; i < mNumPhones; i++) {
                Message response = obtainMessage(EVENT_SET_NW_MODE_FROM_DB_DONE, i, mPrefNwMode[i]);
                mStoredResponse.put(i, response);
            }
        } else {
            for (int i = 0; i < mNumPhones; i++) {
                Message response = obtainMessage(EVENT_SET_NW_MODE_FROM_DB_DONE, i, mPrefNwMode[i]);
                mCi[i].setPreferredNetworkType(mPrefNwMode[i], response);
            }
            mIsSetPrefNwModeInProgress = false;
        }
    }

    private void sendResponseToTarget(Message response, int responseCode) {
        if (response != null) {
            Exception e = CommandException.fromRilErrno(responseCode);
            AsyncResult.forMessage(response, null, e);
            response.sendToTarget();
        }
    }

    private int getNumOfRatSupportedForNwMode(int nwMode,
            ModemCapabilityInfo modemCaps) {
        int supportedRatMaskForNwMode = 0;
        if (modemCaps == null){
            loge("getNumOfRatSupportedForNwMode: nwMode[" + nwMode + "] modemCaps was NULL!");
            return supportedRatMaskForNwMode;
        }
        logd("getNumOfRATsSupportedForNwMode: nwMode[" + nwMode +"] modemCaps = " + modemCaps);

        //send result by ANDing corresponding NETWORK MASK and Modem Caps mask.
        switch (nwMode) {
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_WCDMA_PREF;
                break;

            case RILConstants.NETWORK_MODE_GSM_ONLY:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_GSM_ONLY;
                break;

            case RILConstants.NETWORK_MODE_WCDMA_ONLY:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_WCDMA_ONLY;
                break;

            case RILConstants.NETWORK_MODE_GSM_UMTS:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_GSM_UMTS;
                break;

            case RILConstants.NETWORK_MODE_CDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_CDMA;
                break;

            case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_CDMA_NO_EVDO;
                break;

            case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_EVDO_NO_CDMA;
                break;

            case RILConstants.NETWORK_MODE_GLOBAL:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_GLOBAL;
                break;

            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_LTE_CDMA_EVDO;
                break;

            case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_LTE_GSM_WCDMA;
                break;

            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_LTE_CMDA_EVDO_GSM_WCDMA;
                break;

            case RILConstants.NETWORK_MODE_LTE_ONLY:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_LTE_ONLY;
                break;

            case RILConstants.NETWORK_MODE_LTE_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_LTE_WCDMA;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_ONLY:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_ONLY;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_WCDMA;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_LTE:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_LTE;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_GSM:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_GSM;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_GSM_LTE:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_GSM_LTE;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_GSM_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_GSM_WCDMA;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_WCDMA_LTE:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_WCDMA_LTE;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_GSM_WCDMA_LTE:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_GSM_WCDMA_LTE;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_CDMA_EVDO_GSM_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_CDMA_EVDO_GSM_WCDMA;
                break;

            case RILConstants.NETWORK_MODE_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() &
                        NETWORK_MASK_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA;
                break;

            default:
                break;
        }

        logd("getNumOfRATsSupportedForNwMode: supportedRatMaskForNwMode:" +
                supportedRatMaskForNwMode);

        return getNumRatSupportedInMask(supportedRatMaskForNwMode);
    }

    //This method will return no. of 1's in binary representation of int.
    private int getNumRatSupportedInMask(int mask) {
        int noOfOnes = 0;
        /*
         * To count num of 1's in bitmask.
         * The bitwise and of a number and number - 1, will result in zeroing the least
         *  significant non-zero bit in the number. So if a number has n bits that were set to 1,
         *  then after n iterations of above operation, number will be changed to zero.
         */
        while (mask != 0) {
            mask &= mask - 1;
            noOfOnes++;
        }
        return noOfOnes;
    }
    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    private void loge(String string) {
        Rlog.e(LOG_TAG, string);
    }
}
