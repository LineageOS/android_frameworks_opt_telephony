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

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * This class represents the carrier profiles in the {@link UiccCard}. Each profile contains
 * multiple {@link UiccCardApplication}, one {@link UiccCarrierPrivilegeRules} and one
 * {@link CatService}.
 *
 * Profile is related to {@link android.telephony.SubscriptionInfo} but those two concepts are
 * different. {@link android.telephony.SubscriptionInfo} contains all the subscription information
 * while Profile contains all the {@link UiccCardApplication} which will be used to fetch those
 * subscription information from the {@link UiccCard}.
 *
 * {@hide}
 */
public class UiccProfile extends Handler implements IccCard {
    protected static final String LOG_TAG = "UiccProfile";
    protected static final boolean DBG = true;
    private static final boolean VDBG = false; //STOPSHIP if true
    private static final boolean ICC_CARD_PROXY_REMOVED = true;

    private static final String OPERATOR_BRAND_OVERRIDE_PREFIX = "operator_branding_";

    private final Object mLock = new Object();
    private PinState mUniversalPinState;
    private int mGsmUmtsSubscriptionAppIndex;
    private int mCdmaSubscriptionAppIndex;
    private int mImsSubscriptionAppIndex;
    private UiccCardApplication[] mUiccApplications =
        new UiccCardApplication[IccCardStatus.CARD_MAX_APPS];
    private Context mContext;
    private CommandsInterface mCi;
    private UiccCard mUiccCard; //parent
    private CatService mCatService;
    private UiccCarrierPrivilegeRules mCarrierPrivilegeRules;
    private boolean mDisposed = false;

    private RegistrantList mCarrierPrivilegeRegistrants = new RegistrantList();

    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 15;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 16;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 17;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 18;
    private static final int EVENT_SIM_IO_DONE = 19;
    private static final int EVENT_CARRIER_PRIVILEGES_LOADED = 20;

    private static final LocalLog sLocalLog = new LocalLog(100);

    private final int mPhoneId;

    /*----------------------------------------------------*/
    // logic moved over from IccCardProxy
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 1;
    private static final int EVENT_ICC_LOCKED = 5;
    private static final int EVENT_APP_READY = 6;
    private static final int EVENT_RECORDS_LOADED = 7;
    private static final int EVENT_NETWORK_LOCKED = 9;

    private static final int EVENT_ICC_RECORD_EVENTS = 500;

    private TelephonyManager mTelephonyManager;

    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();

    private int mCurrentAppType = UiccController.APP_FAM_3GPP; //default to 3gpp?
    private UiccCardApplication mUiccApplication = null;
    private IccRecords mIccRecords = null;
    private IccCardConstants.State mExternalState = IccCardConstants.State.UNKNOWN;

    public static final String ACTION_INTERNAL_SIM_STATE_CHANGED =
            "android.intent.action.internal_sim_state_changed";

    /*----------------------------------------------------*/

    public UiccProfile(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId,
            UiccCard uiccCard) {
        if (DBG) log("Creating profile");
        mUiccCard = uiccCard;
        mPhoneId = phoneId;
        if (ICC_CARD_PROXY_REMOVED) {
            // set current app type based on phone type - do this before calling update() as that
            // calls updateIccAvailability() which uses mCurrentAppType
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                setCurrentAppType(phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM);
            }
        }
        update(c, ci, ics);

        if (ICC_CARD_PROXY_REMOVED) {
            ci.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_UNAVAILABLE, null);

            resetProperties();
        }
    }

    /**
     * Dispose the UiccProfile.
     */
    public void dispose() {
        synchronized (mLock) {
            if (DBG) log("Disposing profile");

            unregisterAllAppEvents();
            unregisterCurrAppEvents();

            if (ICC_CARD_PROXY_REMOVED) {
                mCi.unregisterForOffOrNotAvailable(this);
            }

            if (mCatService != null) mCatService.dispose();
            for (UiccCardApplication app : mUiccApplications) {
                if (app != null) {
                    app.dispose();
                }
            }
            mCatService = null;
            mUiccApplications = null;
            mCarrierPrivilegeRules = null;
            mDisposed = true;
        }
    }

    /**
     * The card application that the external world sees will be based on the
     * voice radio technology only!
     */
    public void setVoiceRadioTech(int radioTech) {
        synchronized (mLock) {
            if (DBG) {
                log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(radioTech));
            }
            setCurrentAppType(ServiceState.isGsm(radioTech));
            updateIccAvailability(false);
        }
    }

    private void setCurrentAppType(boolean isGsm) {
        if (VDBG) log("setCurrentAppType");
        synchronized (mLock) {
            boolean isLteOnCdmaMode = TelephonyManager.getLteOnCdmaModeStatic()
                    == PhoneConstants.LTE_ON_CDMA_TRUE;
            if (isGsm || isLteOnCdmaMode) {
                mCurrentAppType = UiccController.APP_FAM_3GPP;
            } else {
                mCurrentAppType = UiccController.APP_FAM_3GPP2;
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (mDisposed) {
            loge("handleMessage: Received " + msg.what + " after dispose(); ignoring the message");
            return;
        }
        switch (msg.what) {
            case EVENT_NETWORK_LOCKED:
                mNetworkLockedRegistrants.notifyRegistrants();
                // intentional fall through
            case EVENT_RADIO_OFF_OR_UNAVAILABLE:
            case EVENT_ICC_LOCKED:
            case EVENT_APP_READY:
            case EVENT_RECORDS_LOADED:
                if (VDBG) log("handleMessage: Received " + msg.what);
                updateExternalState();
                break;

            case EVENT_ICC_RECORD_EVENTS:
                if ((mCurrentAppType == UiccController.APP_FAM_3GPP) && (mIccRecords != null)) {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int eventCode = (Integer) ar.result;
                    if (eventCode == SIMRecords.EVENT_SPN) {
                        mTelephonyManager.setSimOperatorNameForPhone(
                                mPhoneId, mIccRecords.getServiceProviderName());
                    }
                }
                break;

            case EVENT_CARRIER_PRIVILEGES_LOADED:
                if (VDBG) log("EVENT_CARRIER_PRIVILEGES_LOADED");
                onCarrierPriviligesLoadedMessage();
                updateExternalState();
                break;

            case EVENT_OPEN_LOGICAL_CHANNEL_DONE:
            case EVENT_CLOSE_LOGICAL_CHANNEL_DONE:
            case EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE:
            case EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE:
            case EVENT_SIM_IO_DONE:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    loglocal("Exception: " + ar.exception);
                    log("Error in SIM access with exception" + ar.exception);
                }
                AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
                ((Message) ar.userObj).sendToTarget();
                break;

            default:
                loge("Unhandled message with number: " + msg.what);
                break;
        }
    }

    private void updateIccAvailability(boolean allAppsChanged) {
        synchronized (mLock) {
            UiccCardApplication newApp;
            IccRecords newRecords = null;
            newApp = getApplication(mCurrentAppType);
            if (newApp != null) {
                newRecords = newApp.getIccRecords();
            }

            if (allAppsChanged) {
                unregisterAllAppEvents();
                registerAllAppEvents();
            }

            if (mIccRecords != newRecords || mUiccApplication != newApp) {
                if (DBG) log("Icc changed. Reregistering.");
                unregisterCurrAppEvents();
                mUiccApplication = newApp;
                mIccRecords = newRecords;
                registerCurrAppEvents();
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

    private void updateExternalState() {
        // First check if card state is IO_ERROR or RESTRICTED
        if (mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ERROR) {
            setExternalState(IccCardConstants.State.CARD_IO_ERROR);
            return;
        }

        if (mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_RESTRICTED) {
            setExternalState(IccCardConstants.State.CARD_RESTRICTED);
            return;
        }

        // By process of elimination, the UICC Card State = PRESENT and state needs to be decided
        // based on apps
        if (mUiccApplication == null) {
            loge("updateExternalState: setting state to NOT_READY because mUiccApplication is "
                    + "null");
            setExternalState(IccCardConstants.State.NOT_READY);
            return;
        }

        // Check if SIM is locked
        boolean cardLocked = false;
        IccCardConstants.State lockedState = null;
        IccCardApplicationStatus.AppState appState = mUiccApplication.getState();

        PinState pin1State = mUiccApplication.getPin1State();
        if (pin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
            if (VDBG) log("updateExternalState: PERM_DISABLED");
            cardLocked = true;
            lockedState = IccCardConstants.State.PERM_DISABLED;
        } else {
            if (appState == IccCardApplicationStatus.AppState.APPSTATE_PIN) {
                if (VDBG) log("updateExternalState: PIN_REQUIRED");
                cardLocked = true;
                lockedState = IccCardConstants.State.PIN_REQUIRED;
            } else if (appState == IccCardApplicationStatus.AppState.APPSTATE_PUK) {
                if (VDBG) log("updateExternalState: PUK_REQUIRED");
                cardLocked = true;
                lockedState = IccCardConstants.State.PUK_REQUIRED;
            } else if (appState == IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO) {
                if (mUiccApplication.getPersoSubState()
                        == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_SIM_NETWORK) {
                    if (VDBG) log("updateExternalState: PERSOSUBSTATE_SIM_NETWORK");
                    cardLocked = true;
                    lockedState = IccCardConstants.State.NETWORK_LOCKED;
                }
            }
        }

        // If SIM is locked, broadcast state as NOT_READY/LOCKED depending on if records are loaded
        if (cardLocked) {
            if (mIccRecords != null && (mIccRecords.getLockedRecordsLoaded()
                    || mIccRecords.getNetworkLockedRecordsLoaded())) { // locked records loaded
                if (VDBG) {
                    log("updateExternalState: card locked and records loaded; "
                            + "setting state to locked");
                }
                setExternalState(lockedState);
            } else {
                if (VDBG) {
                    log("updateExternalState: card locked but records not loaded; "
                            + "setting state to NOT_READY");
                }
                setExternalState(IccCardConstants.State.NOT_READY);
            }
            return;
        }

        // Check for remaining app states
        switch (appState) {
            case APPSTATE_UNKNOWN:
                /*
                 * APPSTATE_UNKNOWN is a catch-all state reported whenever the app
                 * is not explicitly in one of the other states. To differentiate the
                 * case where we know that there is a card present, but the APP is not
                 * ready, we choose NOT_READY here instead of unknown. This is possible
                 * in at least two cases:
                 * 1) A transient during the process of the SIM bringup
                 * 2) There is no valid App on the SIM to load, which can be the case with an
                 *    eSIM/soft SIM.
                 */
                if (VDBG) {
                    log("updateExternalState: app state is unknown; setting state to NOT_READY");
                }
                setExternalState(IccCardConstants.State.NOT_READY);
                break;
            case APPSTATE_READY:
                if (areAllApplicationsReady()) {
                    if (areAllRecordsLoaded() && areCarrierPriviligeRulesLoaded()) {
                        if (VDBG) log("updateExternalState: setting state to LOADED");
                        setExternalState(IccCardConstants.State.LOADED);
                    } else {
                        if (VDBG) {
                            log("updateExternalState: setting state to READY; records loaded "
                                    + areAllRecordsLoaded() + ", carrier privilige rules loaded "
                                    + areCarrierPriviligeRulesLoaded());
                        }
                        setExternalState(IccCardConstants.State.READY);
                    }
                } else {
                    if (VDBG) {
                        log("updateExternalState: app state is READY but not for all apps; "
                                + "setting state to NOT_READY");
                    }
                    setExternalState(IccCardConstants.State.NOT_READY);
                }
                break;
        }
    }

    private void registerAllAppEvents() {
        // todo: all of these should be notified to UiccProfile directly without needing to register
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                if (VDBG) log("registerUiccCardEvents: registering for EVENT_APP_READY");
                app.registerForReady(this, EVENT_APP_READY, null);
                IccRecords ir = app.getIccRecords();
                if (ir != null) {
                    if (VDBG) log("registerUiccCardEvents: registering for EVENT_RECORDS_LOADED");
                    ir.registerForRecordsLoaded(this, EVENT_RECORDS_LOADED, null);
                    ir.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
                }
            }
        }
    }

    private void unregisterAllAppEvents() {
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                app.unregisterForReady(this);
                IccRecords ir = app.getIccRecords();
                if (ir != null) {
                    ir.unregisterForRecordsLoaded(this);
                    ir.unregisterForRecordsEvents(this);
                }
            }
        }
    }

    private void registerCurrAppEvents() {
        // In case of locked, only listen to the current application.
        if (mIccRecords != null) {
            mIccRecords.registerForLockedRecordsLoaded(this, EVENT_ICC_LOCKED, null);
            mIccRecords.registerForNetworkLockedRecordsLoaded(this, EVENT_NETWORK_LOCKED, null);
        }
    }

    private void unregisterCurrAppEvents() {
        if (mIccRecords != null) {
            mIccRecords.unregisterForLockedRecordsLoaded(this);
            mIccRecords.unregisterForNetworkLockedRecordsLoaded(this);
        }
    }

    static void broadcastInternalIccStateChangedIntent(String value, String reason, int phoneId) {
        Intent intent = new Intent(ACTION_INTERNAL_SIM_STATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
        intent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, value);
        intent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
        intent.putExtra(PhoneConstants.PHONE_KEY, phoneId);  // SubId may not be valid.
        log("Sending intent ACTION_INTERNAL_SIM_STATE_CHANGED value=" + value
                + " for mPhoneId : " + phoneId);
        ActivityManager.broadcastStickyIntent(intent, UserHandle.USER_ALL);
    }

    private void setExternalState(IccCardConstants.State newState, boolean override) {
        synchronized (mLock) {
            if (!SubscriptionManager.isValidSlotIndex(mPhoneId)) {
                loge("setExternalState: mPhoneId=" + mPhoneId + " is invalid; Return!!");
                return;
            }

            if (!override && newState == mExternalState) {
                log("setExternalState: !override and newstate unchanged from " + newState);
                return;
            }
            mExternalState = newState;
            if (mExternalState == IccCardConstants.State.LOADED) {
                // Update the MCC/MNC.
                if (mIccRecords != null) {
                    String operator = mIccRecords.getOperatorNumeric();
                    log("operator=" + operator + " mPhoneId=" + mPhoneId);

                    if (!TextUtils.isEmpty(operator)) {
                        mTelephonyManager.setSimOperatorNumericForPhone(mPhoneId, operator);
                        String countryCode = operator.substring(0, 3);
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
            }
            log("setExternalState: set mPhoneId=" + mPhoneId + " mExternalState=" + mExternalState);
            mTelephonyManager.setSimStateForPhone(mPhoneId, getState().toString());

            broadcastInternalIccStateChangedIntent(getIccStateIntentString(mExternalState),
                    getIccStateReason(mExternalState), mPhoneId);
        }
    }

    private void setExternalState(IccCardConstants.State newState) {
        setExternalState(newState, false);
    }

    /**
     * Function to check if all ICC records have been loaded
     * @return true if all ICC records have been loaded, false otherwise.
     */
    public boolean getIccRecordsLoaded() {
        synchronized (mLock) {
            if (mIccRecords != null) {
                return mIccRecords.getRecordsLoaded();
            }
            return false;
        }
    }

    private String getIccStateIntentString(IccCardConstants.State state) {
        switch (state) {
            case ABSENT: return IccCardConstants.INTENT_VALUE_ICC_ABSENT;
            case PIN_REQUIRED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case PUK_REQUIRED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case NETWORK_LOCKED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case READY: return IccCardConstants.INTENT_VALUE_ICC_READY;
            case NOT_READY: return IccCardConstants.INTENT_VALUE_ICC_NOT_READY;
            case PERM_DISABLED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case CARD_IO_ERROR: return IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR;
            case CARD_RESTRICTED: return IccCardConstants.INTENT_VALUE_ICC_CARD_RESTRICTED;
            case LOADED: return IccCardConstants.INTENT_VALUE_ICC_LOADED;
            default: return IccCardConstants.INTENT_VALUE_ICC_UNKNOWN;
        }
    }

    /**
     * Locked state have a reason (PIN, PUK, NETWORK, PERM_DISABLED, CARD_IO_ERROR)
     * @return reason
     */
    private String getIccStateReason(IccCardConstants.State state) {
        switch (state) {
            case PIN_REQUIRED: return IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN;
            case PUK_REQUIRED: return IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK;
            case NETWORK_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_NETWORK;
            case PERM_DISABLED: return IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED;
            case CARD_IO_ERROR: return IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR;
            case CARD_RESTRICTED: return IccCardConstants.INTENT_VALUE_ICC_CARD_RESTRICTED;
            default: return null;
        }
    }

    /* IccCard interface implementation */
    @Override
    public IccCardConstants.State getState() {
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

    /**
     * Notifies handler of any transition into State.NETWORK_LOCKED
     */
    @Override
    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);

            mNetworkLockedRegistrants.add(r);

            if (getState() == IccCardConstants.State.NETWORK_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForNetworkLocked(Handler h) {
        synchronized (mLock) {
            mNetworkLockedRegistrants.remove(h);
        }
    }

    @Override
    public void supplyPin(String pin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPin(pin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
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
                Exception e = new RuntimeException("ICC card is absent.");
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
                Exception e = new RuntimeException("ICC card is absent.");
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
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
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

    @Override
    public boolean getIccLockEnabled() {
        synchronized (mLock) {
            /* defaults to false, if ICC is absent/deactivated */
            return mUiccApplication != null && mUiccApplication.getIccLockEnabled();
        }
    }

    @Override
    public boolean getIccFdnEnabled() {
        synchronized (mLock) {
            return mUiccApplication != null && mUiccApplication.getIccFdnEnabled();
        }
    }

    @Override
    public boolean getIccPin2Blocked() {
        /* defaults to disabled */
        return mUiccApplication != null && mUiccApplication.getIccPin2Blocked();
    }

    @Override
    public boolean getIccPuk2Blocked() {
        /* defaults to disabled */
        return mUiccApplication != null && mUiccApplication.getIccPuk2Blocked();
    }

    @Override
    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setIccLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
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
                Exception e = new RuntimeException("ICC card is absent.");
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
                Exception e = new RuntimeException("ICC card is absent.");
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
                Exception e = new RuntimeException("ICC card is absent.");
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
    public boolean hasIccCard() {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState()
                    != IccCardStatus.CardState.CARDSTATE_ABSENT) {
                return true;
            }
            loge("hasIccCard: UiccProfile is not null but UiccCard is null or card state is "
                    + "ABSENT");
            return false;
        }
    }

    /**
     * Update the UiccProfile.
     */
    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        synchronized (mLock) {
            mUniversalPinState = ics.mUniversalPinState;
            mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            mContext = c;
            mCi = ci;
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);

            //update applications
            if (DBG) log(ics.mApplications.length + " applications");
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] == null) {
                    //Create newly added Applications
                    if (i < ics.mApplications.length) {
                        mUiccApplications[i] = new UiccCardApplication(this,
                                ics.mApplications[i], mContext, mCi);
                    }
                } else if (i >= ics.mApplications.length) {
                    //Delete removed applications
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                } else {
                    //Update the rest
                    mUiccApplications[i].update(ics.mApplications[i], mContext, mCi);
                }
            }

            createAndUpdateCatServiceLocked();

            // Reload the carrier privilege rules if necessary.
            log("Before privilege rules: " + mCarrierPrivilegeRules + " : " + ics.mCardState);
            if (mCarrierPrivilegeRules == null && ics.mCardState == CardState.CARDSTATE_PRESENT) {
                mCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(this,
                    obtainMessage(EVENT_CARRIER_PRIVILEGES_LOADED));
            } else if (mCarrierPrivilegeRules != null
                    && ics.mCardState != CardState.CARDSTATE_PRESENT) {
                mCarrierPrivilegeRules = null;
            }

            sanitizeApplicationIndexesLocked();
            updateIccAvailability(true);
        }
    }

    private void createAndUpdateCatServiceLocked() {
        if (mUiccApplications.length > 0 && mUiccApplications[0] != null) {
            // Initialize or Reinitialize CatService
            if (mCatService == null) {
                mCatService = CatService.getInstance(mCi, mContext, this, mPhoneId);
            } else {
                mCatService.update(mCi, mContext, this);
            }
        } else {
            if (mCatService != null) {
                mCatService.dispose();
            }
            mCatService = null;
        }
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccProfile finalized");
    }

    /**
     * This function makes sure that application indexes are valid
     * and resets invalid indexes. (This should never happen, but in case
     * RIL misbehaves we need to manage situation gracefully)
     */
    private void sanitizeApplicationIndexesLocked() {
        mGsmUmtsSubscriptionAppIndex =
                checkIndexLocked(
                        mGsmUmtsSubscriptionAppIndex, AppType.APPTYPE_SIM, AppType.APPTYPE_USIM);
        mCdmaSubscriptionAppIndex =
                checkIndexLocked(
                        mCdmaSubscriptionAppIndex, AppType.APPTYPE_RUIM, AppType.APPTYPE_CSIM);
        mImsSubscriptionAppIndex =
                checkIndexLocked(mImsSubscriptionAppIndex, AppType.APPTYPE_ISIM, null);
    }

    private boolean isSupportedApplication(UiccCardApplication app) {
        if (app.getType() != AppType.APPTYPE_USIM && app.getType() != AppType.APPTYPE_CSIM
                && app.getType() != AppType.APPTYPE_ISIM && app.getType() != AppType.APPTYPE_SIM
                && app.getType() != AppType.APPTYPE_RUIM) {
            return false;
        }
        return true;
    }

    private boolean areAllApplicationsReady() {
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null && isSupportedApplication(app) && !app.isReady()) {
                if (VDBG) log("areAllApplicationsReady: return false");
                return false;
            }
        }
        if (VDBG) {
            log("areAllApplicationsReady: outside loop, return " + (mUiccApplication != null));
        }
        return mUiccApplication != null;
    }

    private boolean areAllRecordsLoaded() {
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null && isSupportedApplication(app)) {
                IccRecords ir = app.getIccRecords();
                if (ir == null || !ir.isLoaded()) {
                    if (VDBG) log("areAllRecordsLoaded: return false");
                    return false;
                }
            }
        }
        if (VDBG) {
            log("areAllRecordsLoaded: outside loop, return " + (mUiccApplication != null));
        }
        return mUiccApplication != null;
    }

    private int checkIndexLocked(int index, AppType expectedAppType, AppType altExpectedAppType) {
        if (mUiccApplications == null || index >= mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        }

        if (index < 0) {
            // This is normal. (i.e. no application of this type)
            return -1;
        }

        if (mUiccApplications[index].getType() != expectedAppType
                && mUiccApplications[index].getType() != altExpectedAppType) {
            loge("App index " + index + " is invalid since it's not "
                    + expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }

        // Seems to be valid
        return index;
    }

    /**
     * Registers the handler when carrier privilege rules are loaded.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForCarrierPrivilegeRulesLoaded(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);

            mCarrierPrivilegeRegistrants.add(r);

            if (areCarrierPriviligeRulesLoaded()) {
                r.notifyRegistrant();
            }
        }
    }

    /**
     * Unregister for notifications when carrier privilege rules are loaded.
     *
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForCarrierPrivilegeRulesLoaded(Handler h) {
        synchronized (mLock) {
            mCarrierPrivilegeRegistrants.remove(h);
        }
    }

    private boolean isPackageInstalled(String pkgName) {
        PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES);
            if (DBG) log(pkgName + " is installed.");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            if (DBG) log(pkgName + " is not installed.");
            return false;
        }
    }

    private class ClickListener implements DialogInterface.OnClickListener {
        String mPkgName;
        ClickListener(String pkgName) {
            this.mPkgName = pkgName;
        }
        @Override
        public void onClick(DialogInterface dialog, int which) {
            synchronized (mLock) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    Intent market = new Intent(Intent.ACTION_VIEW);
                    market.setData(Uri.parse("market://details?id=" + mPkgName));
                    market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(market);
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    if (DBG) log("Not now clicked for carrier app dialog.");
                }
            }
        }
    }

    private void promptInstallCarrierApp(String pkgName) {
        DialogInterface.OnClickListener listener = new ClickListener(pkgName);

        Resources r = Resources.getSystem();
        String message = r.getString(R.string.carrier_app_dialog_message);
        String buttonTxt = r.getString(R.string.carrier_app_dialog_button);
        String notNowTxt = r.getString(R.string.carrier_app_dialog_not_now);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setMessage(message)
                .setNegativeButton(notNowTxt, listener)
                .setPositiveButton(buttonTxt, listener)
                .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void onCarrierPriviligesLoadedMessage() {
        UsageStatsManager usm = (UsageStatsManager) mContext.getSystemService(
                Context.USAGE_STATS_SERVICE);
        if (usm != null) {
            usm.onCarrierPrivilegedAppsChanged();
        }
        synchronized (mLock) {
            mCarrierPrivilegeRegistrants.notifyRegistrants();
            String whitelistSetting = Settings.Global.getString(mContext.getContentResolver(),
                    Settings.Global.CARRIER_APP_WHITELIST);
            if (TextUtils.isEmpty(whitelistSetting)) {
                return;
            }
            HashSet<String> carrierAppSet = new HashSet<String>(
                    Arrays.asList(whitelistSetting.split("\\s*;\\s*")));
            if (carrierAppSet.isEmpty()) {
                return;
            }

            List<String> pkgNames = mCarrierPrivilegeRules.getPackageNames();
            for (String pkgName : pkgNames) {
                if (!TextUtils.isEmpty(pkgName) && carrierAppSet.contains(pkgName)
                        && !isPackageInstalled(pkgName)) {
                    promptInstallCarrierApp(pkgName);
                }
            }
        }
    }

    /**
     * Check whether the specified type of application exists in the profile.
     *
     * @param type UICC application type.
     */
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null && mUiccApplications[i].getType() == type) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Return the universal pin state of the profile.
     */
    public PinState getUniversalPinState() {
        synchronized (mLock) {
            return mUniversalPinState;
        }
    }

    /**
     * Return the application of the specified family.
     *
     * @param family UICC application family.
     * @return application corresponding to family or a null if no match found
     */
    public UiccCardApplication getApplication(int family) {
        synchronized (mLock) {
            int index = IccCardStatus.CARD_MAX_APPS;
            switch (family) {
                case UiccController.APP_FAM_3GPP:
                    index = mGsmUmtsSubscriptionAppIndex;
                    break;
                case UiccController.APP_FAM_3GPP2:
                    index = mCdmaSubscriptionAppIndex;
                    break;
                case UiccController.APP_FAM_IMS:
                    index = mImsSubscriptionAppIndex;
                    break;
            }
            if (index >= 0 && index < mUiccApplications.length) {
                return mUiccApplications[index];
            }
            return null;
        }
    }

    /**
     * Return the application with the index of the array.
     *
     * @param index Index of the application array.
     * @return application corresponding to index or a null if no match found
     */
    public UiccCardApplication getApplicationIndex(int index) {
        synchronized (mLock) {
            if (index >= 0 && index < mUiccApplications.length) {
                return mUiccApplications[index];
            }
            return null;
        }
    }

    /**
     * Returns the SIM application of the specified type.
     *
     * @param type ICC application type
     * (@see com.android.internal.telephony.PhoneConstants#APPTYPE_xxx)
     * @return application corresponding to type or a null if no match found
     */
    public UiccCardApplication getApplicationByType(int type) {
        synchronized (mLock) {
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null
                        && mUiccApplications[i].getType().ordinal() == type) {
                    return mUiccApplications[i];
                }
            }
            return null;
        }
    }

    /**
     * Resets the application with the input AID. Returns true if any changes were made.
     *
     * A null aid implies a card level reset - all applications must be reset.
     */
    public boolean resetAppWithAid(String aid) {
        synchronized (mLock) {
            boolean changed = false;
            for (int i = 0; i < mUiccApplications.length; i++) {
                if (mUiccApplications[i] != null
                        && (TextUtils.isEmpty(aid) || aid.equals(mUiccApplications[i].getAid()))) {
                    // Delete removed applications
                    mUiccApplications[i].dispose();
                    mUiccApplications[i] = null;
                    changed = true;
                }
            }
            if (TextUtils.isEmpty(aid)) {
                if (mCarrierPrivilegeRules != null) {
                    mCarrierPrivilegeRules = null;
                    changed = true;
                }
                if (mCatService != null) {
                    mCatService.dispose();
                    mCatService = null;
                    changed = true;
                }
            }
            return changed;
        }
    }

    /**
     * Exposes {@link CommandsInterface#iccOpenLogicalChannel}
     */
    public void iccOpenLogicalChannel(String aid, int p2, Message response) {
        loglocal("Open Logical Channel: " + aid + " , " + p2 + " by pid:" + Binder.getCallingPid()
                + " uid:" + Binder.getCallingUid());
        mCi.iccOpenLogicalChannel(aid, p2,
                obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccCloseLogicalChannel}
     */
    public void iccCloseLogicalChannel(int channel, Message response) {
        loglocal("Close Logical Channel: " + channel);
        mCi.iccCloseLogicalChannel(channel,
                obtainMessage(EVENT_CLOSE_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccTransmitApduLogicalChannel}
     */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduLogicalChannel(channel, cla, command, p1, p2, p3,
                data, obtainMessage(EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccTransmitApduBasicChannel}
     */
    public void iccTransmitApduBasicChannel(int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduBasicChannel(cla, command, p1, p2, p3,
                data, obtainMessage(EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccIO}
     */
    public void iccExchangeSimIO(int fileID, int command, int p1, int p2, int p3,
            String pathID, Message response) {
        mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null,
                obtainMessage(EVENT_SIM_IO_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#sendEnvelopeWithStatus}
     */
    public void sendEnvelopeWithStatus(String contents, Message response) {
        mCi.sendEnvelopeWithStatus(contents, response);
    }

    /**
     * Returns number of applications on this card
     */
    public int getNumApplications() {
        int count = 0;
        for (UiccCardApplication a : mUiccApplications) {
            if (a != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the id of the phone which is associated with this profile.
     */
    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Returns true iff carrier privileges rules are null (dont need to be loaded) or loaded.
     */
    public boolean areCarrierPriviligeRulesLoaded() {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                || carrierPrivilegeRules.areCarrierPriviligeRulesLoaded();
    }

    /**
     * Returns true if there are some carrier privilege rules loaded and specified.
     */
    public boolean hasCarrierPrivilegeRules() {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules != null && carrierPrivilegeRules.hasCarrierPrivilegeRules();
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatus(signature, packageName);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatus(packageManager, packageName);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     */
    public int getCarrierPrivilegeStatus(PackageInfo packageInfo) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatus(packageInfo);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatusForCurrentTransaction}.
     */
    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED :
                carrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction(
                        packageManager);
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPackageNamesForIntent}.
     */
    public List<String> getCarrierPackageNamesForIntent(
            PackageManager packageManager, Intent intent) {
        UiccCarrierPrivilegeRules carrierPrivilegeRules = getCarrierPrivilegeRules();
        return carrierPrivilegeRules == null ? null :
                carrierPrivilegeRules.getCarrierPackageNamesForIntent(
                        packageManager, intent);
    }

    /** Returns a reference to the current {@link UiccCarrierPrivilegeRules}. */
    private UiccCarrierPrivilegeRules getCarrierPrivilegeRules() {
        synchronized (mLock) {
            return mCarrierPrivilegeRules;
        }
    }

    /**
     * Sets the overridden operator brand.
     */
    public boolean setOperatorBrandOverride(String brand) {
        log("setOperatorBrandOverride: " + brand);
        log("current iccId: " + SubscriptionInfo.givePrintableIccid(getIccId()));

        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }

        SharedPreferences.Editor spEditor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        String key = OPERATOR_BRAND_OVERRIDE_PREFIX + iccId;
        if (brand == null) {
            spEditor.remove(key).commit();
        } else {
            spEditor.putString(key, brand).commit();
        }
        return true;
    }

    /**
     * Returns the overridden operator brand.
     */
    public String getOperatorBrandOverride() {
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return null;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        String brandName = sp.getString(OPERATOR_BRAND_OVERRIDE_PREFIX + iccId, null);
        if (brandName == null) {
            // Check if  CarrierConfig sets carrier name
            CarrierConfigManager manager = (CarrierConfigManager)
                    mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            int subId = SubscriptionController.getInstance().getSubIdUsingPhoneId(mPhoneId);
            if (manager != null) {
                PersistableBundle bundle = manager.getConfigForSubId(subId);
                if (bundle != null && bundle.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL)) {
                    brandName = bundle.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING);
                }
            }
        }
        return brandName;
    }

    /**
     * Returns the iccid of the profile.
     */
    public String getIccId() {
        // ICCID should be same across all the apps.
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                IccRecords ir = app.getIccRecords();
                if (ir != null && ir.getIccId() != null) {
                    return ir.getIccId();
                }
            }
        }
        return null;
    }

    private static void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    private void loglocal(String msg) {
        if (DBG) sLocalLog.log(msg);
    }

    /**
     * Dump
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccProfile:");
        pw.println(" mCi=" + mCi);
        pw.println(" mCatService=" + mCatService);
        for (int i = 0; i < mCarrierPrivilegeRegistrants.size(); i++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i + "]="
                    + ((Registrant) mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.println(" mUniversalPinState=" + mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + mImsSubscriptionAppIndex);
        pw.println(" mUiccApplications: length=" + mUiccApplications.length);
        for (int i = 0; i < mUiccApplications.length; i++) {
            if (mUiccApplications[i] == null) {
                pw.println("  mUiccApplications[" + i + "]=" + null);
            } else {
                pw.println("  mUiccApplications[" + i + "]="
                        + mUiccApplications[i].getType() + " " + mUiccApplications[i]);
            }
        }
        pw.println();
        // Print details of all applications
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                app.dump(fd, pw, args);
                pw.println();
            }
        }
        // Print details of all IccRecords
        for (UiccCardApplication app : mUiccApplications) {
            if (app != null) {
                IccRecords ir = app.getIccRecords();
                if (ir != null) {
                    ir.dump(fd, pw, args);
                    pw.println();
                }
            }
        }
        // Print UiccCarrierPrivilegeRules and registrants.
        if (mCarrierPrivilegeRules == null) {
            pw.println(" mCarrierPrivilegeRules: null");
        } else {
            pw.println(" mCarrierPrivilegeRules: " + mCarrierPrivilegeRules);
            mCarrierPrivilegeRules.dump(fd, pw, args);
        }
        pw.println(" mCarrierPrivilegeRegistrants: size=" + mCarrierPrivilegeRegistrants.size());
        for (int i = 0; i < mCarrierPrivilegeRegistrants.size(); i++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i + "]="
                    + ((Registrant) mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.flush();

        if (ICC_CARD_PROXY_REMOVED) {
            pw.println(" mNetworkLockedRegistrants: size=" + mNetworkLockedRegistrants.size());
            for (int i = 0; i < mNetworkLockedRegistrants.size(); i++) {
                pw.println("  mNetworkLockedRegistrants[" + i + "]="
                        + ((Registrant) mNetworkLockedRegistrants.get(i)).getHandler());
            }
            pw.println(" mCurrentAppType=" + mCurrentAppType);
            pw.println(" mUiccCard=" + mUiccCard);
            pw.println(" mUiccApplication=" + mUiccApplication);
            pw.println(" mIccRecords=" + mIccRecords);
            pw.println(" mExternalState=" + mExternalState);
            pw.flush();
        }

        pw.println("sLocalLog:");
        sLocalLog.dump(fd, pw, args);
        pw.flush();
    }
}
