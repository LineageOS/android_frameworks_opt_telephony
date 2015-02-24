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

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import android.provider.Telephony;
import android.content.ContentUris;
import android.net.Uri;
import android.database.Cursor;
import android.text.TextUtils;

import java.util.List;

/**
 *@hide
 */
public class SubscriptionInfoUpdater extends Handler {
    private static final String LOG_TAG = "SubscriptionInfoUpdater";
    private static final int PROJECT_SIM_NUM = TelephonyManager.getDefault().getPhoneCount();

    private static final int EVENT_QUERY_ICCID_DONE = 1;
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 2;
    private static final int EVENT_SIM_LOADED = 3;
    private static final int EVENT_SIM_ABSENT = 4;
    private static final int EVENT_SIM_READY_OR_LOCKED = 5;
    private static final int EVENT_ICC_CHANGED = 6;
    private static final int EVENT_STACK_READY = 7;
    private static final String ICCID_STRING_FOR_NO_SIM = "";
    private static final String ICCID_STRING_FOR_NV = "DUMMY_NV_ID";
    /**
     *  int[] sInsertSimState maintains all slots' SIM inserted status currently,
     *  it may contain 4 kinds of values:
     *    SIM_NOT_INSERT : no SIM inserted in slot i now
     *    SIM_CHANGED    : a valid SIM insert in slot i and is different SIM from last time
     *                     it will later become SIM_NEW or SIM_REPOSITION during update procedure
     *    SIM_NOT_CHANGE : a valid SIM insert in slot i and is the same SIM as last time
     *    SIM_NEW        : a valid SIM insert in slot i and is a new SIM
     *    SIM_REPOSITION : a valid SIM insert in slot i and is inserted in different slot last time
     *    positive integer #: index to distinguish SIM cards with the same IccId
     */
    public static final int SIM_NOT_CHANGE = 0;
    public static final int SIM_CHANGED    = -1;
    public static final int SIM_NEW        = -2;
    public static final int SIM_REPOSITION = -3;
    public static final int SIM_NOT_INSERT = -99;

    public static final int STATUS_NO_SIM_INSERTED = 0x00;
    public static final int STATUS_SIM1_INSERTED = 0x01;
    public static final int STATUS_SIM2_INSERTED = 0x02;
    public static final int STATUS_SIM3_INSERTED = 0x04;
    public static final int STATUS_SIM4_INSERTED = 0x08;

    private static Phone[] mPhone;
    private static Context mContext = null;
    private static CardState[] sCardState = new CardState[PROJECT_SIM_NUM];
    private static UiccController mUiccController = null;
    private static CommandsInterface[] sCi;
    private static IccFileHandler[] mFh = new IccFileHandler[PROJECT_SIM_NUM];
    private static String mIccId[] = new String[PROJECT_SIM_NUM];
    private static int[] mInsertSimState = new int[PROJECT_SIM_NUM];
    private static TelephonyManager mTelephonyMgr = null;
    private SubscriptionManager mSubscriptionManager = null;
    // To prevent repeatedly update flow every time receiver SIM_STATE_CHANGE
    private boolean isNVSubAvailable = false;
    private static boolean mNeedUpdate = true;


    public SubscriptionInfoUpdater(Context context, Phone[] phoneProxy, CommandsInterface[] ci) {
        logd("Constructor invoked");

        mContext = context;
        mPhone = phoneProxy;
        mSubscriptionManager = SubscriptionManager.from(mContext);
        sCi = ci;
        SubscriptionHelper.init(context, ci);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        ModemStackController.getInstance().registerForStackReady(this, EVENT_STACK_READY, null);
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            sCardState[i] = CardState.CARDSTATE_ABSENT;
        }

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(sReceiver, intentFilter);
    }

    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logd("[Receiver]+");
            String action = intent.getAction();
            logd("Action: " + action);

            if (!action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                return;
            }

            int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX);
            logd("slotId: " + slotId);
            if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                return;
            }

            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            logd("simStatus: " + simStatus);

            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                logd("slotId: " + slotId + " simStatus: " + simStatus);
                if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                    return;
                }
                if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(simStatus)
                        || IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(simStatus)) {
                    //TODO: Use RetryManager to limit number of retries and do a exponential backoff
                    if (((PhoneProxy)mPhone[slotId]).getIccFileHandler() != null) {
                        sendMessage(obtainMessage(EVENT_SIM_READY_OR_LOCKED, slotId, -1));
                    }
                } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_LOADED, slotId, -1));
                } else if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_ABSENT, slotId, -1));
                } else {
                    logd("Ignoring simStatus: " + simStatus);
                }
            }
            logd("[Receiver]-");
        }
    };

    private boolean isAllIccIdQueryDone() {
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mIccId[i] == null) {
                logd("Wait for SIM" + (i + 1) + " IccId");
                return false;
            }
        }
        logd("All IccIds query complete");

        return true;
    }

    public void setDisplayNameForNewSub(String newSubName, int subId, int newNameSource) {
        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            // overwrite SIM display name if it is not assigned by user
            int oldNameSource = subInfo.getNameSource();
            CharSequence oldSubName = subInfo.getDisplayName();
            logd("[setDisplayNameForNewSub] subId = " + subInfo.getSubscriptionId()
                    + ", oldSimName = " + oldSubName + ", oldNameSource = " + oldNameSource
                    + ", newSubName = " + newSubName + ", newNameSource = " + newNameSource);
            if (oldSubName == null ||
                (oldNameSource ==
                    SubscriptionManager.NAME_SOURCE_DEFAULT_SOURCE && newSubName != null) ||
                (oldNameSource == SubscriptionManager.NAME_SOURCE_SIM_SOURCE && newSubName != null
                        && !newSubName.equals(oldSubName))) {
                mSubscriptionManager.setDisplayName(newSubName, subInfo.getSubscriptionId(),
                        newNameSource);
            }
        } else {
            logd("SUB" + (subId + 1) + " SubInfo not created yet");
        }
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;
        switch (msg.what) {
            case EVENT_QUERY_ICCID_DONE: {
                Integer slotId = (Integer)ar.userObj;
                logd("handleMessage : <EVENT_QUERY_ICCID_DONE> SIM" + (slotId + 1));
                if (ar.exception == null) {
                    if (ar.result != null) {
                        byte[] data = (byte[])ar.result;
                        mIccId[slotId] = IccUtils.bcdToString(data, 0, data.length);
                    } else {
                        logd("Null ar");
                        mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    }
                } else {
                    mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    logd("Query IccId fail: " + ar.exception);
                }
                logd("mIccId[" + slotId + "] = " + mIccId[slotId]);
                if (isAllIccIdQueryDone() && mNeedUpdate) {
                    updateSubscriptionInfoByIccId();
                }
                break;
            }
            case EVENT_ICC_CHANGED:
                Integer cardIndex = new Integer(PhoneConstants.DEFAULT_CARD_INDEX);
                if (ar.result != null) {
                    cardIndex = (Integer) ar.result;
                } else {
                    Rlog.e(LOG_TAG, "Error: Invalid card index EVENT_ICC_CHANGED ");
                    return;
                }
                updateIccAvailability(cardIndex);
                break;
            case EVENT_STACK_READY:
                logd("EVENT_STACK_READY" );
                if (isAllIccIdQueryDone() && PROJECT_SIM_NUM > 1) {
                    SubscriptionHelper.getInstance().updateSubActivation(mInsertSimState, true);
                }
                break;

            case EVENT_GET_NETWORK_SELECTION_MODE_DONE: {
                Integer slotId = (Integer)ar.userObj;
                if (ar.exception == null && ar.result != null) {
                    int[] modes = (int[])ar.result;
                    if (modes[0] == 1) {  // Manual mode.
                        mPhone[slotId].setNetworkSelectionModeAutomatic(null);
                    }
                } else {
                    logd("EVENT_GET_NETWORK_SELECTION_MODE_DONE: error getting network mode.");
                }
                break;
            }

           case EVENT_SIM_LOADED:
                handleSimLoaded(msg.arg1);
                break;

            case EVENT_SIM_ABSENT:
                handleSimAbsent(msg.arg1);
                break;

            case EVENT_SIM_READY_OR_LOCKED:
                handleSimReadyOrLocked(msg.arg1);
                break;

            default:
                logd("Unknown msg:" + msg.what);
        }
    }

    private void updateIccAvailability(int slotId) {
        if (null == mUiccController) {
            return;
        }
        SubscriptionHelper subHelper = SubscriptionHelper.getInstance();
        logd("updateIccAvailability: Enter, slotId " + slotId);
        if (PROJECT_SIM_NUM > 1 && !subHelper.proceedToHandleIccEvent(slotId)) {
            logd("updateIccAvailability: radio is OFF/unavailable, ignore ");
            if (!subHelper.isApmSIMNotPwdn()) {
                // set the iccid to null so that once SIM card detected
                //  ICCID will be read from the card again.
                mIccId[slotId] = null;
            }
            return;
        }

        CardState newState = CardState.CARDSTATE_ABSENT;
        UiccCard newCard = mUiccController.getUiccCard(slotId);
        if (newCard != null) {
            newState = newCard.getCardState();
            if (!newState.isCardPresent() && isNVSubAvailable) {
                Rlog.i(LOG_TAG, "updateIccAvailability: Returning NV mode ");
                return;
            }
        }
        CardState oldState = sCardState[slotId];
        sCardState[slotId] = newState;
        logd("Slot[" + slotId + "]: New Card State = "
                + newState + " " + "Old Card State = " + oldState);
        if (!newState.isCardPresent()) {
            //Card moved to ABSENT State
            if (mIccId[slotId] != null && !mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
                logd("SIM" + (slotId + 1) + " hot plug out");
                mNeedUpdate = true;
            }
            mFh[slotId] = null;
            mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
            if (isAllIccIdQueryDone() && mNeedUpdate) {
                updateSubscriptionInfoByIccId();
            }
        } else if (!oldState.isCardPresent() && newState.isCardPresent()) {
            // Card moved to PRESENT State.
            if (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
                logd("SIM" + (slotId + 1) + " hot plug in");
                mIccId[slotId] = null;
                mNeedUpdate = true;
            }
            queryIccId(slotId);
        } else if (oldState.isCardPresent() && newState.isCardPresent() &&
                (!subHelper.isApmSIMNotPwdn()) && (mIccId[slotId] == null)) {
            logd("SIM" + (slotId + 1) + " powered up from APM ");
            mFh[slotId] = null;
            mNeedUpdate = true;
            queryIccId(slotId);
        } else if (oldState.isCardPresent() && newState.isCardPresent() &&
                (subHelper.needSubActivationAfterRefresh(slotId))) {
            logd("SIM" + (slotId + 1) + " refresh happened, need sub activation");
            if (isAllIccIdQueryDone()) {
                updateSubscriptionInfoByIccId();
            }
        }
    }
 
   private void handleSimReadyOrLocked(int slotId) {
        if (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug in");
        }
    }

    private void handleSimLoaded(int slotId) {
        logd("handleSimStateLoadedInternal: slotId: " + slotId);
        // The SIM should be loaded at this state, but it is possible in cases such as SIM being
        // removed or a refresh RESET that the IccRecords could be null. The right behavior is to
        // not broadcast the SIM loaded.
        IccRecords records = mPhone[slotId].getIccCard().getIccRecords();
        if (records == null) {  // Possibly a race condition.
            logd("onRecieve: IccRecords null");
            return;
        }
        if (records.getIccId() == null) {
            logd("onRecieve: IccID null");
            return;
        }
        mIccId[slotId] = records.getIccId();

        if (mTelephonyMgr == null) {
            mTelephonyMgr = TelephonyManager.from(mContext);
        }

        int subId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
        int[] subIds = SubscriptionController.getInstance().getSubId(slotId);
        if (subIds != null) {   // Why an array?
            subId = subIds[0];
        }

        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            String operator = records.getOperatorNumeric();
            if (operator != null) {
                if (subId == SubscriptionController.getInstance().getDefaultSubId()) {
                    MccTable.updateMccMncConfiguration(mContext, operator, false);
                }
                SubscriptionController.getInstance().setMccMnc(operator,subId);
            } else {
                logd("EVENT_RECORDS_LOADED Operator name is null");
            }

            String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(subId);
            ContentResolver contentResolver = mContext.getContentResolver();

            if (msisdn != null) {
                ContentValues number = new ContentValues(1);
                number.put(SubscriptionManager.NUMBER, msisdn);
                contentResolver.update(SubscriptionManager.CONTENT_URI, number,
                        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                        + Long.toString(subId), null);
            }

            SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
            String nameToSet;
            String simCarrierName = mTelephonyMgr.getSimOperatorNameForSubscription(subId);
            ContentValues name = new ContentValues(1);

            if (subInfo != null && subInfo.getNameSource() !=
                    SubscriptionManager.NAME_SOURCE_USER_INPUT) {
                if (!TextUtils.isEmpty(simCarrierName)) {
                    nameToSet = simCarrierName;
                } else {
                    nameToSet = "CARD " + Integer.toString(slotId + 1);
                }
                name.put(SubscriptionManager.DISPLAY_NAME, nameToSet);
                logd("sim name = " + nameToSet);
                contentResolver.update(SubscriptionManager.CONTENT_URI, name,
                        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID
                        + "=" + Long.toString(subId), null);
            }

        } else {
            logd("Invalid subId, could not update ContentResolver");
        }
    }

    private void handleSimAbsent(int slotId) {
        if (mIccId[slotId] != null && !mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug out");
        }
    }

    private void queryIccId(int slotId) {
        logd("queryIccId: slotid=" + slotId);
        if (mFh[slotId] == null) {
            logd("Getting IccFileHandler");

            UiccCardApplication validApp = null;
            UiccCard uiccCard = mUiccController.getUiccCard(slotId);
            int numApps = uiccCard.getNumApplications();
            for (int i = 0; i < numApps; i++) {
                UiccCardApplication app = uiccCard.getApplicationIndex(i);
                if (app != null && app.getType() != AppType.APPTYPE_UNKNOWN) {
                    validApp = app;
                    break;
                }
            }
            if (validApp != null) mFh[slotId] = validApp.getIccFileHandler();
        }
        if (mFh[slotId] != null) {
            String iccId = mIccId[slotId];
            if (iccId == null) {
                logd("Querying IccId");
                mFh[slotId].loadEFTransparent(IccConstants.EF_ICCID,
                        obtainMessage(EVENT_QUERY_ICCID_DONE, new Integer(slotId)));
            } else {
                logd("NOT Querying IccId its already set sIccid[" + slotId + "]=" + iccId);
            }
        } else {
            //Reset to CardState to ABSENT so that on next EVENT_ICC_CHANGED, ICCID can be read.
            sCardState[slotId] = CardState.CARDSTATE_ABSENT;
            logd("mFh[" + slotId + "] is null, SIM not inserted");
        }
    }

    public void updateSubIdForNV(int slotId) {
        mIccId[slotId] = ICCID_STRING_FOR_NV;
        mNeedUpdate = true;
        logd("[updateSubIdForNV]+ Start");
        if (isAllIccIdQueryDone()) {
            logd("[updateSubIdForNV]+ updating");
            updateSubscriptionInfoByIccId();
            isNVSubAvailable = true;
        }
    }

    /**
     * TODO: Simplify more, as no one is interested in what happened
     * only what the current list contains.
     */
    synchronized private void updateSubscriptionInfoByIccId() {
        logd("updateSubscriptionInfoByIccId:+ Start");
        mNeedUpdate = false;

        mSubscriptionManager.clearSubscriptionInfo();

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            mInsertSimState[i] = SIM_NOT_CHANGE;
        }

        int insertedSimCount = PROJECT_SIM_NUM;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (ICCID_STRING_FOR_NO_SIM.equals(mIccId[i])) {
                insertedSimCount--;
                mInsertSimState[i] = SIM_NOT_INSERT;
            }
        }
        logd("insertedSimCount = " + insertedSimCount);

        int index = 0;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == SIM_NOT_INSERT) {
                continue;
            }
            index = 2;
            for (int j = i + 1; j < PROJECT_SIM_NUM; j++) {
                if (mInsertSimState[j] == SIM_NOT_CHANGE && mIccId[i].equals(mIccId[j])) {
                    mInsertSimState[i] = 1;
                    mInsertSimState[j] = index;
                    index++;
                }
            }
        }

        ContentResolver contentResolver = mContext.getContentResolver();
        String[] oldIccId = new String[PROJECT_SIM_NUM];
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            oldIccId[i] = null;
            List<SubscriptionInfo> oldSubInfo =
                    SubscriptionController.getInstance().getSubInfoUsingSlotIdWithCheck(i, false);
            if (oldSubInfo != null) {
                oldIccId[i] = oldSubInfo.get(0).getIccId();
                logd("updateSubscriptionInfoByIccId: oldSubId = "
                        + oldSubInfo.get(0).getSubscriptionId());
                if (mInsertSimState[i] == SIM_NOT_CHANGE && !mIccId[i].equals(oldIccId[i])) {
                    mInsertSimState[i] = SIM_CHANGED;
                }
                if (mInsertSimState[i] != SIM_NOT_CHANGE) {
                    ContentValues value = new ContentValues(1);
                    value.put(SubscriptionManager.SIM_SLOT_INDEX,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, value,
                            SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                            + Integer.toString(oldSubInfo.get(0).getSubscriptionId()), null);
                }
            } else {
                if (mInsertSimState[i] == SIM_NOT_CHANGE) {
                    // no SIM inserted last time, but there is one SIM inserted now
                    mInsertSimState[i] = SIM_CHANGED;
                }
                oldIccId[i] = ICCID_STRING_FOR_NO_SIM;
                logd("updateSubscriptionInfoByIccId: No SIM in slot " + i + " last time");
            }
        }

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            logd("updateSubscriptionInfoByIccId: oldIccId[" + i + "] = " + oldIccId[i] +
                    ", mIccId[" + i + "] = " + mIccId[i]);
        }

        //check if the inserted SIM is new SIM
        int nNewCardCount = 0;
        int nNewSimStatus = 0;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == SIM_NOT_INSERT) {
                logd("No SIM inserted in slot " + i + " this time");
                if (PROJECT_SIM_NUM == 1) {
                    SubscriptionController.getInstance().updateUserPrefs(false);
                }
            } else {
                if (mInsertSimState[i] > 0) {
                    //some special SIMs may have the same IccIds, add suffix to distinguish them
                    //FIXME: addSubInfoRecord can return an error.
                    mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i]
                            + Integer.toString(mInsertSimState[i]), i);
                    logd("SUB" + (i + 1) + " has invalid IccId");
                } else /*if (mInsertSimState[i] != SIM_NOT_INSERT)*/ {
                    mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i], i);
                }
                if (isNewSim(mIccId[i], oldIccId)) {
                    nNewCardCount++;
                    switch (i) {
                        case PhoneConstants.SUB1:
                            nNewSimStatus |= STATUS_SIM1_INSERTED;
                            break;
                        case PhoneConstants.SUB2:
                            nNewSimStatus |= STATUS_SIM2_INSERTED;
                            break;
                        case PhoneConstants.SUB3:
                            nNewSimStatus |= STATUS_SIM3_INSERTED;
                            break;
                        //case PhoneConstants.SUB3:
                        //    nNewSimStatus |= STATUS_SIM4_INSERTED;
                        //    break;
                    }

                    mInsertSimState[i] = SIM_NEW;
                }
            }
        }

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == SIM_CHANGED) {
                mInsertSimState[i] = SIM_REPOSITION;
            }
            logd("updateSubscriptionInfoByIccId: mInsertSimState[" + i + "] = "
                    + mInsertSimState[i]);
        }

        SubscriptionHelper.getInstance().updateNwMode();
        if (ModemStackController.getInstance().isStackReady() && PROJECT_SIM_NUM > 1) {
            SubscriptionHelper.getInstance().updateSubActivation(mInsertSimState, false);
        }

        List<SubscriptionInfo> subInfos = mSubscriptionManager.getActiveSubscriptionInfoList();
        int nSubCount = (subInfos == null) ? 0 : subInfos.size();
        logd("updateSubscriptionInfoByIccId: nSubCount = " + nSubCount);
        for (int i=0; i < nSubCount; i++) {
            SubscriptionInfo temp = subInfos.get(i);

            String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(
                    temp.getSubscriptionId());

            if (msisdn != null) {
                ContentValues value = new ContentValues(1);
                value.put(SubscriptionManager.NUMBER, msisdn);
                contentResolver.update(SubscriptionManager.CONTENT_URI, value,
                        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                        + Integer.toString(temp.getSubscriptionId()), null);
            }
        }

        SubscriptionController.getInstance().notifySubscriptionInfoChanged();
        logd("updateSubscriptionInfoByIccId:- SsubscriptionInfo update complete");
    }

    private boolean isNewSim(String iccId, String[] oldIccId) {
        boolean newSim = true;
        for(int i = 0; i < PROJECT_SIM_NUM; i++) {
            if(iccId.equals(oldIccId[i])) {
                newSim = false;
                break;
            }
        }
        logd("newSim = " + newSim);

        return newSim;
    }

    public void dispose() {
        logd("[dispose]");
        mContext.unregisterReceiver(sReceiver);
    }

    private void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }
}
