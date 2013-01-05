/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardApplicationStatus;
import com.android.internal.telephony.IccCardStatus;

import java.util.ArrayList;

/**
 * Qualcomm RIL class for basebands that do not send the SIM status
 * piggybacked in RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED. Instead,
 * these radios will send radio state and we have to query for SIM
 * status separately.
 *
 * {@hide}
 */
public class HTCQualcommRIL extends RIL implements CommandsInterface {
    protected HandlerThread mIccThread;
    protected IccHandler mIccHandler;
    boolean skipCdmaSubcription = needsOldRilFeature("skipCdmaSubcription");

    private static final int RIL_UNSOL_ENTER_LPM = 1523;
    private static final int RIL_UNSOL_CDMA_3G_INDICATOR = 3009;
    private static final int RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR = 3012;
    private static final int RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE = 6002;
    private static final int RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED = 21004;
    private static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED = 21005;
    private static final int RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED = 21007;

    private final int RIL_INT_RADIO_OFF = 0;
    private final int RIL_INT_RADIO_UNAVALIABLE = 1;
    private final int RIL_INT_RADIO_ON = 2;
    private final int RIL_INT_RADIO_ON_NG = 10;
    private final int RIL_INT_RADIO_ON_HTC = 13;

    public HTCQualcommRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplicationStatus appStatus;

        // use old needsOldRilFeature method for feature. it would be redundant to make
        // a new method just for naming sake.
        boolean oldRil = needsOldRilFeature("icccardstatus");

        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();

        if (!oldRil)
            cardStatus.mImsSubscriptionAppIndex = p.readInt();

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];

        for (int i = 0 ; i < numApplications ; i++) {
            appStatus = new IccCardApplicationStatus();
            appStatus.app_type       = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state      = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            if ((appStatus.app_state == IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO) &&
                ((appStatus.perso_substate == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_READY) ||
                (appStatus.perso_substate == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_UNKNOWN))) {
                // ridiculous HTC hack
                appStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                Log.d(LOG_TAG, "ca.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO");
                Log.d(LOG_TAG, "ca.perso_substate == PersoSubState.PERSOSUBSTATE_READY");
            }
            appStatus.aid            = p.readString();
            appStatus.app_label      = p.readString();
            appStatus.pin1_replaced  = p.readInt();
            appStatus.pin1           = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2           = appStatus.PinStateFromRILInt(p.readInt());
            cardStatus.mApplications[i] = appStatus;
        }
        return cardStatus;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        /* HTC signal strength format:
         * 0: GW_SignalStrength
         * 1: GW_SignalStrength.bitErrorRate
         * 2: CDMA_SignalStrength.dbm
         * 3: CDMA_SignalStrength.ecio
         * 4: EVDO_SignalStrength.dbm
         * 5: EVDO_SignalStrength.ecio
         * 6: EVDO_SignalStrength.signalNoiseRatio
         * 7: ATT_SignalStrength.dbm
         * 8: ATT_SignalStrength.ecno
         * 9: LTE_SignalStrength.signalStrength
         * 10: LTE_SignalStrength.rsrp
         * 11: LTE_SignalStrength.rsrq
         * 12: LTE_SignalStrength.rssnr
         * 13: LTE_SignalStrength.cqi
         */

        int gsmSignalStrength = p.readInt();
        int gsmBitErrorRate = p.readInt();
        int cdmaDbm = p.readInt();
        int cdmaEcio = p.readInt();
        int evdoDbm = p.readInt();
        int evdoEcio = p.readInt();
        int evdoSnr = p.readInt();
        p.readInt(); // ATT_SignalStrength.dbm
        p.readInt(); // ATT_SignalStrength.ecno
        int lteSignalStrength = p.readInt();
        int lteRsrp = p.readInt();
        int lteRsrq = p.readInt();
        int lteRssnr = p.readInt();
        int lteCqi = p.readInt();
        boolean isGsm = (mPhoneType == RILConstants.GSM_PHONE);

        SignalStrength signalStrength = new SignalStrength(gsmSignalStrength,
                gsmBitErrorRate, cdmaDbm, cdmaEcio, evdoDbm, evdoEcio, evdoSnr,
                lteSignalStrength, lteRsrp, lteRsrq, lteRssnr, lteCqi, isGsm);

        return signalStrength;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_ENTER_LPM: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_3G_INDICATOR:  ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR:  ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE:  ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED: ret = responseVoid(p); break;

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case RIL_UNSOL_ENTER_LPM:
            case RIL_UNSOL_CDMA_3G_INDICATOR:
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR:
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE:
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED:
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;
        }
    }

    /**
     * Notify all registrants that the ril has connected or disconnected.
     *
     * @param rilVer is the version of the ril or -1 if disconnected.
     */
    private void notifyRegistrantsRilConnectionChanged(int rilVer) {
        mRilVersion = rilVer;
        if (mRilConnectedRegistrants != null) {
            mRilConnectedRegistrants.notifyRegistrants(
                                new AsyncResult (null, new Integer(rilVer), null));
        }
    }

    private void setRadioStateFromRILInt (int stateCode) {
        CommandsInterface.RadioState radioState;
        HandlerThread handlerThread;
        Looper looper;
        IccHandler iccHandler;

        switch (stateCode) {
            case RIL_INT_RADIO_OFF:
                radioState = CommandsInterface.RadioState.RADIO_OFF;
                if (mIccHandler != null) {
                    mIccThread = null;
                    mIccHandler = null;
                }
                break;
            case RIL_INT_RADIO_UNAVALIABLE:
                radioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case RIL_INT_RADIO_ON:
            case RIL_INT_RADIO_ON_NG:
            case RIL_INT_RADIO_ON_HTC:
                if (mIccHandler == null) {
                    handlerThread = new HandlerThread("IccHandler");
                    mIccThread = handlerThread;

                    mIccThread.start();

                    looper = mIccThread.getLooper();
                    mIccHandler = new IccHandler(this,looper);
                    mIccHandler.run();
                }
                radioState = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateCode);
        }

        setRadioState (radioState);
    }

    class IccHandler extends Handler implements Runnable {
        private static final int EVENT_RADIO_ON = 1;
        private static final int EVENT_ICC_STATUS_CHANGED = 2;
        private static final int EVENT_GET_ICC_STATUS_DONE = 3;
        private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 4;

        private RIL mRil;
        private boolean mRadioOn = false;

        public IccHandler (RIL ril, Looper looper) {
            super (looper);
            mRil = ril;
        }

        public void handleMessage (Message paramMessage) {
            switch (paramMessage.what) {
                case EVENT_RADIO_ON:
                    mRadioOn = true;
                    Log.d(LOG_TAG, "Radio on -> Forcing sim status update");
                    sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    AsyncResult asyncResult = (AsyncResult) paramMessage.obj;
                    if (asyncResult.exception != null) {
                        Log.e (LOG_TAG, "IccCardStatusDone shouldn't return exceptions!", asyncResult.exception);
                        break;
                    }
                    IccCardStatus status = (IccCardStatus) asyncResult.result;
                    if (status.mApplications == null || status.mApplications.length == 0) {
                        if (!mRil.getRadioState().isOn()) {
                            break;
                        }

                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                    } else {
                        int appIndex = -1;
                        if (mPhoneType == RILConstants.CDMA_PHONE && !skipCdmaSubcription) {
                            appIndex = status.mCdmaSubscriptionAppIndex;
                            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
                        } else {
                            appIndex = status.mGsmUmtsSubscriptionAppIndex;
                            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
                        }

                        IccCardApplicationStatus application = status.mApplications[appIndex];
                        IccCardApplicationStatus.AppState app_state = application.app_state;
                        IccCardApplicationStatus.AppType app_type = application.app_type;

                        switch (app_state) {
                            case APPSTATE_PIN:
                            case APPSTATE_PUK:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            case APPSTATE_READY:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            default:
                                return;
                        }
                    }
                    break;
                case EVENT_ICC_STATUS_CHANGED:
                    if (mRadioOn) {
                        Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                         mRil.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, paramMessage.obj));
                    } else {
                         Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED while radio is not ON. Ignoring");
                    }
                    break;
                case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                    mRadioOn = false;
                    // disposeCards(); // to be verified;
                default:
                    Log.e(LOG_TAG, " Unknown Event " + paramMessage.what);
                    break;
            }
        }

        public void run () {
            mRil.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
            Message msg = obtainMessage(EVENT_RADIO_ON);
            mRil.getIccCardStatus(msg);
        }
    }
}
