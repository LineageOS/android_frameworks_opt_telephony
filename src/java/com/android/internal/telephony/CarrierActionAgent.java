/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Carrier Action Agent(CAA) paired with
 * {@link com.android.internal.telephony.CarrierSignalAgent CarrierSignalAgent},
 * serves as an agent to dispatch carrier actions from carrier apps to different telephony modules,
 * {@link android.telephony.TelephonyManager#carrierActionSetRadioEnabled(int, boolean)
 * carrierActionSetRadioEnabled} for example.
 *
 * CAA supports dynamic registration where different telephony modules could listen for a specific
 * carrier action event and implement their own handler. CCA will dispatch the event to all
 * interested parties and maintain the received action states internally for future inspection.
 * Each CarrierActionAgent is associated with a phone object.
 */
public class CarrierActionAgent extends Handler {
    private static final String LOG_TAG = "CarrierActionAgent";
    private static final boolean DBG = true;
    private static final boolean VDBG = Rlog.isLoggable(LOG_TAG, Log.VERBOSE);

    /** A list of carrier actions */
    public static final int CARRIER_ACTION_SET_METERED_APNS_ENABLED      = 0;
    public static final int CARRIER_ACTION_SET_RADIO_ENABLED             = 1;

    /** Member variables */
    private final Phone mPhone;
    /** registrant list per carrier action */
    private RegistrantList mMeteredApnEnableRegistrants = new RegistrantList();
    private RegistrantList mRadioEnableRegistrants = new RegistrantList();
    /** local log for carrier actions */
    private LocalLog mMeteredApnEnabledLog = new LocalLog(10);
    private LocalLog mRadioEnabledLog = new LocalLog(10);
    /** carrier actions, true by default */
    private Boolean mCarrierActionOnMeteredApnEnabled = true;
    private Boolean mCarrierActionOnRadioEnabled = true;

    /** Constructor */
    public CarrierActionAgent(Phone phone) {
        mPhone = phone;
        if (DBG) log("Creating CarrierActionAgent");
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case CARRIER_ACTION_SET_METERED_APNS_ENABLED:
                mCarrierActionOnMeteredApnEnabled = (boolean) msg.obj;
                log("SET_METERED_APNS_ENABLED: " + mCarrierActionOnMeteredApnEnabled);
                mMeteredApnEnabledLog.log("SET_METERED_APNS_ENABLED: "
                        + mCarrierActionOnMeteredApnEnabled);
                mMeteredApnEnableRegistrants.notifyRegistrants(
                        new AsyncResult(null, mCarrierActionOnMeteredApnEnabled, null));
                break;
            case CARRIER_ACTION_SET_RADIO_ENABLED:
                mCarrierActionOnRadioEnabled = (boolean) msg.obj;
                log("SET_RADIO_ENABLED: " + mCarrierActionOnRadioEnabled);
                mRadioEnabledLog.log("SET_RADIO_ENABLED: " + mCarrierActionOnRadioEnabled);
                mRadioEnableRegistrants.notifyRegistrants(
                        new AsyncResult(null, mCarrierActionOnRadioEnabled, null));
                break;
            default:
                loge("Unknown carrier action: " + msg.what);
        }
    }

    /**
     * Return current carrier action values
     */
    public Object getCarrierActionValue(int action) {
        Object val = getCarrierAction(action);
        if (val == null) {
            throw new IllegalArgumentException("invalid carrier action: " + action);
        }
        return val;
    }

    /**
     * Action set from carrier app to enable/disable radio
     */
    public void carrierActionSetRadioEnabled(boolean enabled) {
        sendMessage(obtainMessage(CARRIER_ACTION_SET_RADIO_ENABLED, enabled));
    }

    /**
     * Action set from carrier app to enable/disable metered APNs
     */
    public void carrierActionSetMeteredApnsEnabled(boolean enabled) {
        sendMessage(obtainMessage(CARRIER_ACTION_SET_METERED_APNS_ENABLED, enabled));
    }

    private RegistrantList getRegistrantsFromAction(int action) {
        switch (action) {
            case CARRIER_ACTION_SET_METERED_APNS_ENABLED:
                return mMeteredApnEnableRegistrants;
            case CARRIER_ACTION_SET_RADIO_ENABLED:
                return mRadioEnableRegistrants;
            default:
                loge("Unsupported action: " + action);
                return null;
        }
    }

    private Object getCarrierAction(int action) {
        switch (action) {
            case CARRIER_ACTION_SET_METERED_APNS_ENABLED:
                return mCarrierActionOnMeteredApnEnabled;
            case CARRIER_ACTION_SET_RADIO_ENABLED:
                return mCarrierActionOnRadioEnabled;
            default:
                loge("Unsupported action: " + action);
                return null;
        }
    }

    /**
     * Register with CAA for a specific event.
     * @param action which carrier action registrant is interested in
     * @param notifyNow if carrier action has once set, notify registrant right after
     *                  registering, so that registrants will get the latest carrier action.
     */
    public void registerForCarrierAction(int action, Handler h, int what, Object obj,
                                         boolean notifyNow) {
        Object carrierAction = getCarrierAction(action);
        if (carrierAction == null) {
            throw new IllegalArgumentException("invalid carrier action: " + action);
        }
        RegistrantList list = getRegistrantsFromAction(action);
        Registrant r = new Registrant(h, what, obj);
        list.add(r);
        if (notifyNow) {
            r.notifyRegistrant(new AsyncResult(null, carrierAction, null));
        }
    }

    /**
     * Unregister with CAA for a specific event. Callers will no longer be notified upon such event.
     * @param action which carrier action caller is no longer interested in
     */
    public void unregisterForCarrierAction(Handler h, int action) {
        RegistrantList list = getRegistrantsFromAction(action);
        if (list == null) {
            throw new IllegalArgumentException("invalid carrier action: " + action);
        }
        list.remove(h);
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    private void loge(String s) {
        Rlog.e(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    private void logv(String s) {
        Rlog.v(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        pw.println(" mCarrierActionOnMeteredApnsEnabled Log:");
        ipw.increaseIndent();
        mMeteredApnEnabledLog.dump(fd, ipw, args);
        ipw.decreaseIndent();

        pw.println(" mCarrierActionOnRadioEnabled Log:");
        ipw.increaseIndent();
        mRadioEnabledLog.dump(fd, ipw, args);
        ipw.decreaseIndent();
    }
}
