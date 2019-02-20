/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.telephony.PhoneCapability;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * This class manages phone's configuration which defines the potential capability (static) of the
 * phone and its current activated capability (current).
 * It gets and monitors static and current phone capability from the modem; send broadcast
 * if they change, and and sends commands to modem to enable or disable phones.
 */
public class PhoneConfigurationManager {
    public static final String DSDA = "dsda";
    public static final String DSDS = "dsds";
    public static final String TSTS = "tsts";
    public static final String SSSS = "";
    private static final String LOG_TAG = "PhoneCfgMgr";
    private static final int EVENT_SWITCH_DSDS_CONFIG_DONE = 100;

    private static PhoneConfigurationManager sInstance = null;
    private final Context mContext;
    private PhoneCapability mStaticCapability;
    private PhoneCapability mCurrentCapability;
    private final RadioConfig mRadioConfig;
    private final MainThreadHandler mHandler;

    /**
     * Init method to instantiate the object
     * Should only be called once.
     */
    public static PhoneConfigurationManager init(Context context) {
        synchronized (PhoneConfigurationManager.class) {
            if (sInstance == null) {
                sInstance = new PhoneConfigurationManager(context);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /**
     * Constructor.
     * @param context context needed to send broadcast.
     */
    private PhoneConfigurationManager(Context context) {
        mContext = context;
        // TODO: send commands to modem once interface is ready.
        TelephonyManager telephonyManager = new TelephonyManager(context);
        mStaticCapability = PhoneConfigurationModels.DSDS_CAPABILITY;
        mCurrentCapability = mStaticCapability;
        mRadioConfig = RadioConfig.getInstance(mContext);
        mHandler = new MainThreadHandler();

        notifyCapabilityChanged();
    }

    /**
     * Static method to get instance.
     */
    public static PhoneConfigurationManager getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }

        return sInstance;
    }

    /**
     * Handler class to handle callbacks
     */
    private final class MainThreadHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SWITCH_DSDS_CONFIG_DONE:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.exception == null) {
                        int numOfLiveModems = msg.arg1;
                        setMultiSimProperties(numOfLiveModems);
                    } else {
                        log(msg.what + " failure. Not switching multi-sim config." + ar.exception);
                    }
                    break;
            }
        }
    }

    /**
     * Enable or disable phone
     *
     * @param phone which phone to operate on
     * @param enable true or false
     * @param result the message to sent back when it's done.
     */
    public void enablePhone(Phone phone, boolean enable, Message result) {
        if (phone == null) {
            log("enablePhone failed phone is null");
            return;
        }
        phone.mCi.enableModem(enable, result);
    }

    /**
     * Returns how many phone objects the device supports.
     */
    public int getPhoneCount() {
        TelephonyManager tm = new TelephonyManager(mContext);
        return tm.getPhoneCount();
    }

    /**
     * get static overall phone capabilities for all phones.
     */
    public PhoneCapability getStaticPhoneCapability() {
        return mStaticCapability;
    }

    /**
     * get configuration related status of each phone.
     */
    public PhoneCapability getCurrentPhoneCapability() {
        return mCurrentCapability;
    }

    public int getNumberOfModemsWithSimultaneousDataConnections() {
        return mCurrentCapability.maxActiveData;
    }

    private void notifyCapabilityChanged() {
        PhoneNotifier notifier = new DefaultPhoneNotifier();

        notifier.notifyPhoneCapabilityChanged(mCurrentCapability);
    }

    /**
     * Switch configs to enable multi-sim or switch back to single-sim
     * @param numOfSims number of active sims we want to switch to
     */
    public void switchMultiSimConfig(int numOfSims) {
        log("switchMultiSimConfig: with numOfSims = " + numOfSims);
        if (getStaticPhoneCapability().logicalModemList.size() < numOfSims) {
            log("switchMultiSimConfig: Phone is not capable of enabling "
                    + numOfSims + " sims, exiting!");
            return;
        }
        if (getPhoneCount() != numOfSims) {
            log("switchMultiSimConfig: sending the request for switching");
            Message callback = Message.obtain(
                    mHandler, EVENT_SWITCH_DSDS_CONFIG_DONE, numOfSims, 0 /**dummy arg*/);
            mRadioConfig.setModemsConfig(numOfSims, callback);
        } else {
            log("switchMultiSimConfig: No need to switch. getNumOfActiveSims is already "
                    + numOfSims);
        }
    }

    /**
     * Get whether reboot is required or not after making changes to modem configurations.
     * Return value defaults to true
     */
    public boolean isRebootRequiredForModemConfigChange() {
        String rebootRequired = SystemProperties.get(
                TelephonyProperties.PROPERTY_REBOOT_REQUIRED_ON_MODEM_CHANGE);
        log("isRebootRequiredForModemConfigChange: isRebootRequired = " + rebootRequired);
        return !rebootRequired.equals("false");
    }

    /**
     * Helper method to set system properties for setting multi sim configs,
     * as well as doing the phone reboot
     * NOTE: In order to support more than 3 sims, we need to change this method.
     * @param numOfSims number of active sims
     */
    private void setMultiSimProperties(int numOfSims) {
        String finalMultiSimConfig;
        switch(numOfSims) {
            case 3:
                finalMultiSimConfig = TSTS;
                break;
            case 2:
                finalMultiSimConfig = DSDS;
                break;
            default:
                finalMultiSimConfig = SSSS;
        }

        SystemProperties.set(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG, finalMultiSimConfig);
        if (isRebootRequiredForModemConfigChange()) {
            log("setMultiSimProperties: Rebooting due to switching multi-sim config to "
                    + finalMultiSimConfig);
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            pm.reboot("Switching to " + finalMultiSimConfig);
        } else {
            log("setMultiSimProperties: Rebooting is not required to switch multi-sim config to "
                    + finalMultiSimConfig);
        }
    }

    private static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
