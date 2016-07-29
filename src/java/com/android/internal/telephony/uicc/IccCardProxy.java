/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.Manifest.permission.READ_PHONE_STATE;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.android.internal.telephony.uicc.UiccController;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
// import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
// import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * @Deprecated use {@link UiccController}.getUiccCard instead.
 *
 * The Phone App assumes that there is only one icc card, and one icc application
 * available at a time. Moreover, it assumes such object (represented with IccCard)
 * is available all the time (whether {@link RILConstants#RIL_REQUEST_GET_SIM_STATUS} returned
 * or not, whether card has desired application or not, whether there really is a card in the
 * slot or not).
 *
 * UiccController, however, can handle multiple instances of icc objects (multiple
 * {@link UiccCardApplication}, multiple {@link IccFileHandler}, multiple {@link IccRecords})
 * created and destroyed dynamically during phone operation.
 *
 * This class implements the IccCard interface that is always available (right after default
 * phone object is constructed) to expose the current (based on voice radio technology)
 * application on the uicc card, so that external apps won't break.
 */

public class IccCardProxy extends Handler implements IccCard {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "IccCardProxy";

    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_ICC_CHANGED = 3;
    private static final int EVENT_ICC_ABSENT = 4;
    private static final int EVENT_ICC_LOCKED = 5;
    private static final int EVENT_APP_READY = 6;
    private static final int EVENT_RECORDS_LOADED = 7;
    private static final int EVENT_IMSI_READY = 8;
    private static final int EVENT_NETWORK_LOCKED = 9;
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 11;

    private static final int EVENT_ICC_RECORD_EVENTS = 500;
    private static final int EVENT_SUBSCRIPTION_ACTIVATED = 501;
    private static final int EVENT_SUBSCRIPTION_DEACTIVATED = 502;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 503;

    private Integer mPhoneId = null;

    private final Object mLock = new Object();
    private Context mContext;
    private CommandsInterface mCi;
    private TelephonyManager mTelephonyManager;

    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();

    private int mCurrentAppType = UiccController.APP_FAM_3GPP; //default to 3gpp?
    private UiccController mUiccController = null;
    private UiccCard mUiccCard = null;
    private UiccCardApplication mUiccApplication = null;
    private IccRecords mIccRecords = null;
    private CdmaSubscriptionSourceManager mCdmaSSM = null;
    private boolean mRadioOn = false;
    private boolean mQuietMode = false; // when set to true IccCardProxy will not broadcast
                                        // ACTION_SIM_STATE_CHANGED intents
    private boolean mInitialized = false;
    private State mExternalState = State.UNKNOWN;
    private PersoSubState mPersoSubState = PersoSubState.PERSOSUBSTATE_UNKNOWN;

    public static final String ACTION_INTERNAL_SIM_STATE_CHANGED = "android.intent.action.internal_sim_state_changed";

    public IccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        if (DBG) log("ctor: ci=" + ci + " phoneId=" + phoneId);
        mContext = context;
        mCi = ci;
        mPhoneId = phoneId;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context,
                ci, this, EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED, null);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        mUiccController.registerForIccRecovery(this, EVENT_ICC_RECOVERY, null);  // MTK
        ci.registerForOn(this,EVENT_RADIO_ON, null);
        ci.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_UNAVAILABLE, null);
        ci.registerForNotAvailable(this, EVENT_NOT_AVAILABLE, null);  // MTK

        resetProperties();
        setExternalState(State.NOT_READY, false);
    }

    public void dispose() {
        synchronized (mLock) {
            log("Disposing");
            //Cleanup icc references
            mUiccController.unregisterForIccChanged(this);
            mUiccController.unregisterForIccRecovery(this);  // MTK
            mUiccController = null;
            mCi.unregisterForOn(this);
            mCi.unregisterForOffOrNotAvailable(this);
            mCi.unregisterForNotAvailable(this);  // MTK
            mCdmaSSM.dispose(this);
        }
    }

    /*
     * The card application that the external world sees will be based on the
     * voice radio technology only!
     */
    public void setVoiceRadioTech(int radioTech) {
        synchronized (mLock) {
            if (DBG) {
                log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(radioTech));
            }
            if (ServiceState.isGsm(radioTech)) {
                mCurrentAppType = UiccController.APP_FAM_3GPP;
            } else {
                mCurrentAppType = UiccController.APP_FAM_3GPP2;
            }
            updateQuietMode();
        }
    }

    /**
     * In case of 3gpp2 we need to find out if subscription used is coming from
     * NV in which case we shouldn't broadcast any sim states changes.
     */
    private void updateQuietMode() {
        synchronized (mLock) {
            boolean oldQuietMode = mQuietMode;
            boolean newQuietMode;
            int cdmaSource = Phone.CDMA_SUBSCRIPTION_UNKNOWN;
            if (mCurrentAppType == UiccController.APP_FAM_3GPP) {
                newQuietMode = false;
                if (DBG) log("updateQuietMode: 3GPP subscription -> newQuietMode=" + newQuietMode);
            } else {
                cdmaSource = mCdmaSSM != null ?
                        mCdmaSSM.getCdmaSubscriptionSource() : Phone.CDMA_SUBSCRIPTION_UNKNOWN;

                newQuietMode = (cdmaSource == Phone.CDMA_SUBSCRIPTION_NV)
                        && (mCurrentAppType == UiccController.APP_FAM_3GPP2);
            }

            if (mQuietMode == false && newQuietMode == true) {
                // Last thing to do before switching to quiet mode is
                // broadcast ICC_READY
                log("Switching to QuietMode.");
                setExternalState(State.READY);
                mQuietMode = newQuietMode;
            } else if (mQuietMode == true && newQuietMode == false) {
                if (DBG) {
                    log("updateQuietMode: Switching out from QuietMode."
                            + " Force broadcast of current state=" + mExternalState);
                }
                mQuietMode = newQuietMode;
                setExternalState(mExternalState, true);
            } else {
                if (DBG) log("updateQuietMode: no changes don't setExternalState");
            }
            if (DBG) {
                log("updateQuietMode: QuietMode is " + mQuietMode + " (app_type="
                    + mCurrentAppType + " cdmaSource=" + cdmaSource + ")");
            }
            mInitialized = true;
            sendMessage(obtainMessage(EVENT_ICC_CHANGED));
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                mRadioOn = false;
                if (CommandsInterface.RadioState.RADIO_UNAVAILABLE == mCi.getRadioState()) {
                    setExternalState(State.NOT_READY);
                }
                break;
            case EVENT_RADIO_ON:
                mRadioOn = true;
                if (!mInitialized) {
                    updateQuietMode();
                }
                break;
            case EVENT_ICC_CHANGED:
                // For CDMA Nv device, should not handle Icc change since no SIM card.
                if (mQuietMode) {
                    log("QuietMode: ignore ICC changed ");
                    return;
                }
                if (mInitialized) {
                    // MTK
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int index = mPhoneId;

                    if (ar != null && ar.result instanceof Integer) {
                        index = ((Integer) ar.result).intValue();
                        log("handleMessage (EVENT_ICC_CHANGED) , index = " + index);
                    } else {
                        log("handleMessage (EVENT_ICC_CHANGED), come from myself");
                    }

                    // SVLTE
                    // MTK TODO
                    /*
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && index == 100) {
                        index = SvlteModeController.getCdmaSocketSlotId();
                    }
                    */
                    if (index == mPhoneId) {
                        updateIccAvailability();
                    }
                }
                break;
            case EVENT_ICC_ABSENT:
                // For CDMA Nv device, should not handle Icc absent since no SIM Card.
                if (mQuietMode) {
                    log("QuietMode: ignore SIM absent ");
                    return;
                }
                mAbsentRegistrants.notifyRegistrants();
                setExternalState(State.ABSENT);
                break;
            case EVENT_ICC_LOCKED:
                processLockedState();
                break;
            case EVENT_APP_READY:
                setExternalState(State.READY);
                break;
            case EVENT_RECORDS_LOADED:
                // Update the MCC/MNC.
                if (mIccRecords != null) {
                    String operator = mIccRecords.getOperatorNumeric();
                    log("operator=" + operator + " mPhoneId=" + mPhoneId);

                    if (operator != null) {
                        log("update icc_operator_numeric=" + operator);
                        mTelephonyManager.setSimOperatorNumericForPhone(mPhoneId, operator);
                        String countryCode = operator.substring(0,3);
                        if (countryCode != null) {
                            mTelephonyManager.setSimCountryIsoForPhone(mPhoneId,
                                    MccTable.countryCodeForMcc(Integer.parseInt(countryCode)));
                        } else {
                            loge("EVENT_RECORDS_LOADED Country code is null");
                        }
                    } else {
                        loge("EVENT_RECORDS_LOADED Operator name is null");
                    }
                }
                if (mUiccCard != null && !mUiccCard.areCarrierPriviligeRulesLoaded()) {
                    mUiccCard.registerForCarrierPrivilegeRulesLoaded(
                        this, EVENT_CARRIER_PRIVILIGES_LOADED, null);
                } else {
                    onRecordsLoaded();
                }
                break;
            case EVENT_IMSI_READY:
                broadcastIccStateChangedIntent(IccCardConstants.INTENT_VALUE_ICC_IMSI, null);
                break;
            case EVENT_NETWORK_LOCKED:
                // MTK
                if (mUiccApplication == null) {
                    loge("getIccStateReason: NETWORK_LOCKED but mUiccApplication is null!");
                    return;
                }
                mPersoSubState = mUiccApplication.getPersoSubState();
                mNetworkLockedRegistrants.notifyRegistrants((AsyncResult)msg.obj);
                setExternalState(State.NETWORK_LOCKED);
                break;
            case EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
                updateQuietMode();
                break;
            case EVENT_SUBSCRIPTION_ACTIVATED:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                break;

            case EVENT_SUBSCRIPTION_DEACTIVATED:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                break;

            case EVENT_ICC_RECORD_EVENTS:
                if ((mCurrentAppType == UiccController.APP_FAM_3GPP) && (mIccRecords != null)) {
                    AsyncResult ar = (AsyncResult)msg.obj;
                    int eventCode = (Integer) ar.result;
                    if (eventCode == SIMRecords.EVENT_SPN) {
                        mTelephonyManager.setSimOperatorNameForPhone(
                                mPhoneId, mIccRecords.getServiceProviderName());
                    }
                }
                break;

            case EVENT_CARRIER_PRIVILIGES_LOADED:
                log("EVENT_CARRIER_PRIVILEGES_LOADED");
                if (mUiccCard != null) {
                    mUiccCard.unregisterForCarrierPrivilegeRulesLoaded(this);
                }
                onRecordsLoaded();
                break;

            // MTK
            case EVENT_NOT_AVAILABLE:
                log("handleMessage (EVENT_NOT_AVAILABLE)");
                setExternalState(State.NOT_READY);
                break;

            case EVENT_ICC_RECOVERY: {
                AsyncResult ar = (AsyncResult) msg.obj;
                Integer index = (Integer) ar.result;
                log("handleMessage (EVENT_ICC_RECOVERY) , index = " + index);
                if (index == mPhoneId) {
                    if (DBG) log("mRecoveryRegistrants notify");
                    mRecoveryRegistrants.notifyRegistrants();
                }
                break;
            }

            case EVENT_ICC_FDN_CHANGED:
                mFdnChangedRegistrants.notifyRegistrants();
                break;

            case EVENT_ICC_REFRESH:
                log("EVENT_ICC_REFRESH");
                if (mIccRecords != null) {
                    mIccRecords.onRefresh(true, null);
                }
                break;

            default:
                loge("Unhandled message with number: " + msg.what);
                break;
        }
    }

    private void onSubscriptionActivated() {
        updateIccAvailability();
        updateStateProperty();
    }

    private void onSubscriptionDeactivated() {
        resetProperties();
        updateIccAvailability();
        updateStateProperty();
    }

    private void onRecordsLoaded() {
        broadcastInternalIccStateChangedIntent(IccCardConstants.INTENT_VALUE_ICC_LOADED, null);
    }

    private void updateIccAvailability() {
        synchronized (mLock) {
            UiccCard newCard = mUiccController.getUiccCard(mPhoneId);
            CardState state = CardState.CARDSTATE_ABSENT;
            UiccCardApplication newApp = null;
            IccRecords newRecords = null;
            if (newCard != null) {
                state = newCard.getCardState();
                newApp = newCard.getApplication(mCurrentAppType);
                if (newApp != null) {
                    newRecords = newApp.getIccRecords();
                }
            }

            if (mIccRecords != newRecords || mUiccApplication != newApp || mUiccCard != newCard) {
                if (DBG) log("Icc changed. Reregestering.");
                unregisterUiccCardEvents();
                mUiccCard = newCard;
                mUiccApplication = newApp;
                mIccRecords = newRecords;
                registerUiccCardEvents();
            }
            updateExternalState();
        }
    }

    void resetProperties() {
        if (mCurrentAppType == UiccController.APP_FAM_3GPP) {
            log("update icc_operator_numeric=" + "");
            mTelephonyManager.setSimOperatorNumericForPhone(mPhoneId, "");
            mTelephonyManager.setSimCountryIsoForPhone(mPhoneId, "");
            mTelephonyManager.setSimOperatorNameForPhone(mPhoneId, "");
         }
    }

    private void HandleDetectedState() {
    // CAF_MSIM SAND
//        setExternalState(State.DETECTED, false);
    }

    private void updateExternalState() {

        // mUiccCard could be null at bootup, before valid card states have
        // been received from UiccController.
        if (mUiccCard == null) {
            setExternalState(State.NOT_READY);
            return;
        }

        if (mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
            if (mRadioOn) {
                setExternalState(State.ABSENT);
            } else {
                setExternalState(State.NOT_READY);
            }
            return;
        }

        if (mUiccCard.getCardState() == CardState.CARDSTATE_ERROR) {
            setExternalState(State.CARD_IO_ERROR);
            return;
        }

        if (mUiccApplication == null) {
            setExternalState(State.NOT_READY);
            return;
        }

        switch (mUiccApplication.getState()) {
            case APPSTATE_UNKNOWN:
                setExternalState(State.UNKNOWN);
                break;
            case APPSTATE_DETECTED:
                HandleDetectedState();
                break;
            case APPSTATE_PIN:
                setExternalState(State.PIN_REQUIRED);
                break;
            case APPSTATE_PUK:
                setExternalState(State.PUK_REQUIRED);
                break;
            case APPSTATE_SUBSCRIPTION_PERSO:
                if (mUiccApplication.isPersoLocked()) {
                    mPersoSubState = mUiccApplication.getPersoSubState();
                    setExternalState(State.NETWORK_LOCKED);
                } else {
                    setExternalState(State.UNKNOWN);
                }
                break;
            case APPSTATE_READY:
                setExternalState(State.READY);
                break;
            // MTK
            default:
                setExternalState(State.UNKNOWN);
                break;
        }
    }

    private void registerUiccCardEvents() {
        if (mUiccCard != null) {
            mUiccCard.registerForAbsent(this, EVENT_ICC_ABSENT, null);
        }
        if (mUiccApplication != null) {
            mUiccApplication.registerForReady(this, EVENT_APP_READY, null);
            mUiccApplication.registerForLocked(this, EVENT_ICC_LOCKED, null);
            mUiccApplication.registerForNetworkLocked(this, EVENT_NETWORK_LOCKED, null);
            // MTK
            mUiccApplication.registerForFdnChanged(this, EVENT_ICC_FDN_CHANGED, null);
        }
        if (mIccRecords != null) {
            mIccRecords.registerForImsiReady(this, EVENT_IMSI_READY, null);
            mIccRecords.registerForRecordsLoaded(this, EVENT_RECORDS_LOADED, null);
            mIccRecords.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        }
    }

    private void unregisterUiccCardEvents() {
        if (mUiccCard != null) mUiccCard.unregisterForAbsent(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForReady(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForNetworkLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForFdnChanged(this);  // MTK
        if (mIccRecords != null) mIccRecords.unregisterForImsiReady(this);
        if (mIccRecords != null) mIccRecords.unregisterForRecordsLoaded(this);
        if (mIccRecords != null) mIccRecords.unregisterForRecordsEvents(this);
    }

    private void updateStateProperty() {
        mTelephonyManager.setSimStateForPhone(mPhoneId, getState().toString());
    }

    private void broadcastIccStateChangedIntent(String value, String reason) {
        synchronized (mLock) {
            if (mPhoneId == null || !SubscriptionManager.isValidSlotId(mPhoneId)) {
                loge("broadcastIccStateChangedIntent: mPhoneId=" + mPhoneId
                        + " is invalid; Return!!");
                return;
            }

            if (mQuietMode) {
                log("broadcastIccStateChangedIntent: QuietMode"
                        + " NOT Broadcasting intent ACTION_SIM_STATE_CHANGED "
                        + " value=" +  value + " reason=" + reason);
                return;
            }

            Intent intent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            // TODO - we'd like this intent to have a single snapshot of all sim state,
            // but until then this should not use REPLACE_PENDING or we may lose
            // information
            // intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
            intent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, value);
            intent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhoneId);
            log("broadcastIccStateChangedIntent intent ACTION_SIM_STATE_CHANGED value=" + value
                + " reason=" + reason + " for mPhoneId=" + mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE,
                    UserHandle.USER_ALL);
        }
    }

    private void broadcastInternalIccStateChangedIntent(String value, String reason) {
        synchronized (mLock) {
            if (mPhoneId == null) {
                loge("broadcastInternalIccStateChangedIntent: Card Index is not set; Return!!");
                return;
            }

            Intent intent = new Intent(ACTION_INTERNAL_SIM_STATE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                    | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
            intent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, value);
            intent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
            intent.putExtra(PhoneConstants.PHONE_KEY, mPhoneId);  // SubId may not be valid.
            log("Sending intent ACTION_INTERNAL_SIM_STATE_CHANGED" + " for mPhoneId : " + mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        }
    }

    private void setExternalState(State newState, boolean override) {
        synchronized (mLock) {
            if (mPhoneId == null || !SubscriptionManager.isValidSlotId(mPhoneId)) {
                loge("setExternalState: mPhoneId=" + mPhoneId + " is invalid; Return!!");
                return;
            }

            if (!override && newState == mExternalState) {
                loge("setExternalState: !override and newstate unchanged from " + newState);
                return;
            }
            mExternalState = newState;
            loge("setExternalState: set mPhoneId=" + mPhoneId + " mExternalState=" + mExternalState);
            mTelephonyManager.setSimStateForPhone(mPhoneId, getState().toString());

            // For locked states, we should be sending internal broadcast.
            if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(getIccStateIntentString(mExternalState))) {
                broadcastInternalIccStateChangedIntent(getIccStateIntentString(mExternalState),
                        getIccStateReason(mExternalState));
            } else {
                broadcastIccStateChangedIntent(getIccStateIntentString(mExternalState),
                        getIccStateReason(mExternalState));
            }
            // TODO: Need to notify registrants for other states as well.
            if ( State.ABSENT == mExternalState) {
                mAbsentRegistrants.notifyRegistrants();
            }
        }
    }

    private void processLockedState() {
        synchronized (mLock) {
            if (mUiccApplication == null) {
                //Don't need to do anything if non-existent application is locked
                return;
            }
            PinState pin1State = mUiccApplication.getPin1State();
            if (pin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                setExternalState(State.PERM_DISABLED);
                return;
            }

            AppState appState = mUiccApplication.getState();
            switch (appState) {
                case APPSTATE_PIN:
                    mPinLockedRegistrants.notifyRegistrants();
                    setExternalState(State.PIN_REQUIRED);
                    break;
                case APPSTATE_PUK:
                    setExternalState(State.PUK_REQUIRED);
                    break;
                case APPSTATE_DETECTED:
                case APPSTATE_READY:
                case APPSTATE_SUBSCRIPTION_PERSO:
                case APPSTATE_UNKNOWN:
                    // Neither required
                    break;
            }
        }
    }

    private void setExternalState(State newState) {
        // MTK
        if (newState == State.PIN_REQUIRED && mUiccApplication != null) {
            PinState pin1State = mUiccApplication.getPin1State();
            if (pin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                setExternalState(State.PERM_DISABLED);
                return;
            }
        }

        setExternalState(newState, false);
    }

    public boolean getIccRecordsLoaded() {
        synchronized (mLock) {
            if (mIccRecords != null) {
                return mIccRecords.getRecordsLoaded();
            }
            return false;
        }
    }

    private String getIccStateIntentString(State state) {
        switch (state) {
            case ABSENT: return IccCardConstants.INTENT_VALUE_ICC_ABSENT;
            case PIN_REQUIRED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case PUK_REQUIRED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case NETWORK_LOCKED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case READY: return IccCardConstants.INTENT_VALUE_ICC_READY;
            case NOT_READY: return IccCardConstants.INTENT_VALUE_ICC_NOT_READY;
            case PERM_DISABLED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case CARD_IO_ERROR: return IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR;
            default: return IccCardConstants.INTENT_VALUE_ICC_UNKNOWN;
        }
    }

    /**
     * Locked state have a reason (PIN, PUK, NETWORK, PERM_DISABLED, CARD_IO_ERROR)
     * @return reason
     */
    private String getIccStateReason(State state) {
        switch (state) {
            case PIN_REQUIRED: return IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN;
            case PUK_REQUIRED: return IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK;
            case NETWORK_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_NETWORK;
            // MTK TODO
            /*
            case NETWORK_LOCKED:
                switch (mUiccApplication.getPersoSubState()) {
                        case PERSOSUBSTATE_SIM_NETWORK:
                            return IccCardConstants.INTENT_VALUE_LOCKED_NETWORK;
                        case PERSOSUBSTATE_SIM_NETWORK_SUBSET:
                            return IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_SUBSET;
                        case PERSOSUBSTATE_SIM_CORPORATE:
                            return IccCardConstants.INTENT_VALUE_LOCKED_CORPORATE;
                        case PERSOSUBSTATE_SIM_SERVICE_PROVIDER:
                            return IccCardConstants.INTENT_VALUE_LOCKED_SERVICE_PROVIDER;
                        case PERSOSUBSTATE_SIM_SIM:
                            return IccCardConstants.INTENT_VALUE_LOCKED_SIM;
                        default: return null;
                }
            */
            case PERM_DISABLED: return IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED;
            case CARD_IO_ERROR: return IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR;
            default: return null;
       }
    }

    /* IccCard interface implementation */
    @Override
    public State getState() {
        synchronized (mLock) {
            return mExternalState;
        }
    }

    @Override
    public IccRecords getIccRecords() {
        synchronized (mLock) {
            return mIccRecords;
        }
    }

    @Override
    public IccFileHandler getIccFileHandler() {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                return mUiccApplication.getIccFileHandler();
            }
            return null;
        }
    }

    /**
     * Notifies handler of any transition into State.ABSENT
     */
    @Override
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mAbsentRegistrants.add(r);

            if (getState() == State.ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForAbsent(Handler h) {
        synchronized (mLock) {
            mAbsentRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler of any transition into State.NETWORK_LOCKED
     */
    @Override
    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mNetworkLockedRegistrants.add(r);

            if (getState() == State.NETWORK_LOCKED) {
                r.notifyRegistrant(new AsyncResult(null, mPersoSubState.ordinal(), null));
            }
        }
    }

    @Override
    public void unregisterForNetworkLocked(Handler h) {
        synchronized (mLock) {
            mNetworkLockedRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler of any transition into State.isPinLocked()
     */
    @Override
    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mPinLockedRegistrants.add(r);

            if (getState().isPinLocked()) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForLocked(Handler h) {
        synchronized (mLock) {
            mPinLockedRegistrants.remove(h);
        }
    }

    @Override
    public void supplyPin(String pin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPin(pin, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to supplyPin, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyPuk(String puk, String newPin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPuk(puk, newPin, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to supplyPuk, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyPin2(String pin2, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPin2(pin2, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to supplyPin2, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyPuk2(String puk2, String newPin2, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPuk2(puk2, newPin2, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to supplyPuk2, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyNetworkDepersonalization(String pin, String type, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyNetworkDepersonalization(pin, type, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("CommandsInterface is not set.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public boolean getIccLockEnabled() {
        synchronized (mLock) {
            /* defaults to false, if ICC is absent/deactivated */
            Boolean retValue = mUiccApplication != null ?
                    mUiccApplication.getIccLockEnabled() : false;
            return retValue;
        }
    }

    @Override
    public boolean getIccFdnEnabled() {
        synchronized (mLock) {
            Boolean retValue = mUiccApplication != null ?
                    mUiccApplication.getIccFdnEnabled() : false;
            return retValue;
        }
    }

    public boolean getIccFdnAvailable() {
        boolean retValue = mUiccApplication != null ? mUiccApplication.getIccFdnAvailable() : false;
        return retValue;
    }

    public boolean getIccPin2Blocked() {
        /* defaults to disabled */
        Boolean retValue = mUiccApplication != null ? mUiccApplication.getIccPin2Blocked() : false;
        return retValue;
    }

    public boolean getIccPuk2Blocked() {
        /* defaults to disabled */
        Boolean retValue = mUiccApplication != null ? mUiccApplication.getIccPuk2Blocked() : false;
        return retValue;
    }

    @Override
    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setIccLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to setIccLockEnabled, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setIccFdnEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setIccFdnEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to setIccFdnEnabled, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void changeIccLockPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.changeIccLockPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to changeIccLockPassword, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void changeIccFdnPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.changeIccFdnPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                // MTK
                // Exception e = new RuntimeException("ICC card is absent.");
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to changeIccFdnPassword, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public String getServiceProviderName() {
        synchronized (mLock) {
            if (mIccRecords != null) {
                return mIccRecords.getServiceProviderName();
            }
            return null;
        }
    }

    @Override
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            Boolean retValue = mUiccCard != null ? mUiccCard.isApplicationOnIcc(type) : false;
            return retValue;
        }
    }

    @Override
    public boolean hasIccCard() {
        synchronized (mLock) {
            // MTK
            /*
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                return true;
            }
            return false;
            */
            boolean isSimInsert = false;

            // To obtain correct status earily,
            // we use system property value to detemine sim inserted state.
            String iccId = null;
            iccId = SystemProperties.get(PROPERTY_ICCID_SIM[mPhoneId]);

            if (DBG) log("iccId = " + iccId);
            if ((iccId != null) && !(iccId.equals("")) && !(iccId.equals(ICCID_STRING_FOR_NO_SIM))) {
                isSimInsert = true;
            }

            if (!isSimInsert && mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                isSimInsert = true;
            }

            if (DBG) log("hasIccCard(): isSimInsert =  " + isSimInsert + " ,CardState = " + ((mUiccCard != null) ? mUiccCard.getCardState() : ""));

            return isSimInsert;
        }
    }

    private void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(mPhoneId, property, value);
    }

    public IccRecords getIccRecord() {
        return mIccRecords;
    }
    private void log(String s) {
        Rlog.d(LOG_TAG, s /* MTK */ + " (slot " + mPhoneId + ")");
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg /* MTK */ + " (slot " + mPhoneId + ")");
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IccCardProxy: " + this);
        pw.println(" mContext=" + mContext);
        pw.println(" mCi=" + mCi);
        pw.println(" mAbsentRegistrants: size=" + mAbsentRegistrants.size());
        for (int i = 0; i < mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]="
                    + ((Registrant)mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + mPinLockedRegistrants.size());
        for (int i = 0; i < mPinLockedRegistrants.size(); i++) {
            pw.println("  mPinLockedRegistrants[" + i + "]="
                    + ((Registrant)mPinLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mNetworkLockedRegistrants: size=" + mNetworkLockedRegistrants.size());
        for (int i = 0; i < mNetworkLockedRegistrants.size(); i++) {
            pw.println("  mNetworkLockedRegistrants[" + i + "]="
                    + ((Registrant)mNetworkLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mCurrentAppType=" + mCurrentAppType);
        pw.println(" mUiccController=" + mUiccController);
        pw.println(" mUiccCard=" + mUiccCard);
        pw.println(" mUiccApplication=" + mUiccApplication);
        pw.println(" mIccRecords=" + mIccRecords);
        pw.println(" mCdmaSSM=" + mCdmaSSM);
        pw.println(" mRadioOn=" + mRadioOn);
        pw.println(" mQuietMode=" + mQuietMode);
        pw.println(" mInitialized=" + mInitialized);
        pw.println(" mExternalState=" + mExternalState);

        pw.flush();
    }

    // MTK

    private static final int EVENT_ICC_REFRESH = 12;

    private static final int EVENT_ICC_RECOVERY = 100;
    private static final int EVENT_ICC_FDN_CHANGED = 101;
    private static final int EVENT_NOT_AVAILABLE = 102;

    private RegistrantList mRecoveryRegistrants = new RegistrantList();
    private RegistrantList mFdnChangedRegistrants = new RegistrantList();

    // private PersoSubState mNetworkLockState = PersoSubState.PERSOSUBSTATE_UNKNOWN;


    private static final String ICCID_STRING_FOR_NO_SIM = "N/A";
    private String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };

    private static final String COMMON_SLOT_PROPERTY = "";
    private static Intent sInternalIntent = null;

    /**
     * Refresh and load all of sim files if active phone is switched to svltephone.
     */
    public void updateIccRefresh() {
        sendMessage(obtainMessage(EVENT_ICC_REFRESH));
    }

    @Override
    public void supplyNetworkDepersonalization(String pin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyNetworkDepersonalization(pin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("CommandsInterface is not set.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    /**
     * Query the SIM ME Lock type required to unlock.
     *
     * @return SIM ME Lock type
     */
    public PersoSubState getNetworkPersoType() {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                return mUiccApplication.getPersoSubState();
            }
            return PersoSubState.PERSOSUBSTATE_UNKNOWN;
        }
    }

    /**
     * Check whether ICC network lock is enabled
     * This is an async call which returns lock state to applications directly
     */
    @Override
    public void queryIccNetworkLock(int category, Message onComplete) {
        if (DBG) log("queryIccNetworkLock(): category =  " + category);
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.queryIccNetworkLock(category, onComplete);
            } else if (onComplete != null) {
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to queryIccNetworkLock, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    /**
     * Set the ICC network lock enabled or disabled
     * When the operation is complete, onComplete will be sent to its handler
     */
    @Override
    public void setIccNetworkLockEnabled(int category,
            int lockop, String password, String data_imsi, String gid1, String gid2, Message onComplete) {
        if (DBG) log("SetIccNetworkEnabled(): category = " + category
            + " lockop = " + lockop + " password = " + password
            + " data_imsi = " + data_imsi + " gid1 = " + gid1 + " gid2 = " + gid2);
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setIccNetworkLockEnabled(
                    category, lockop, password, data_imsi, gid1, gid2, onComplete);
            } else if (onComplete != null) {
                Exception e = CommandException.fromRilErrno(RILConstants.RADIO_NOT_AVAILABLE);
                log("Fail to setIccNetworkLockEnabled, hasIccCard = " + hasIccCard());
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    /**
     * Used by SIM ME lock related enhancement feature(Modem SML change feature).
     */
    public void repollIccStateForModemSmlChangeFeatrue(boolean needIntent) {
        if (DBG) log("repollIccStateForModemSmlChangeFeatrue, needIntent = " + needIntent);
        synchronized (mLock) {
            mUiccController.repollIccStateForModemSmlChangeFeatrue(mPhoneId, needIntent);
        }
    }

    public void exchangeSimIo(int fileID, int command,
                              int p1, int p2, int p3, String pathID, String data, String pin2, Message onComplete) {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                mUiccCard.exchangeSimIo(fileID, command, p1, p2, p3, pathID,
                        data, pin2, onComplete);
            }
        }
    }

    public void iccGetAtr(Message onComplete) {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                mUiccCard.iccGetAtr(onComplete);
            }
        }
    }

    public void openLogicalChannelWithSw(String AID, Message onComplete) {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                mUiccCard.iccOpenChannelWithSw(AID, onComplete);
            }
        }
    }

    // retrun usim property or use uicccardapplication app type
    public String getIccCardType() {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                return mUiccCard.getIccCardType();
            }
            return "";
        }
    }

    // MTK-START
    /**
     * Request to get SVLTE UICC card type.
     *
     * @return index for UICC card type
     *
     */
    public int getSvlteCardType() {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                return mUiccCard.getSvlteCardType();
            }
            return 0;
        }
    }
    // MTK-END

    public void registerForRecovery(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);

            mRecoveryRegistrants.add(r);

            if (getState() == State.READY) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForRecovery(Handler h) {
        synchronized (mLock) {
            mRecoveryRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler in case of FDN changed
     */
    @Override
    public void registerForFdnChanged(Handler h, int what, Object obj) {
        synchronized (mLock) {
            synchronized (mLock) {
                Registrant r = new Registrant(h, what, obj);

                mFdnChangedRegistrants.add(r);

                if (getIccFdnEnabled()) {
                    r.notifyRegistrant();
                }
            }
        }
    }

    @Override
    public void unregisterForFdnChanged(Handler h) {
        synchronized (mLock) {
            mFdnChangedRegistrants.remove(h);
        }
    }
}
