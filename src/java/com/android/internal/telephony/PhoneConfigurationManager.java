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
import android.os.storage.StorageManager;
import android.telephony.PhoneCapability;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

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
    private static final int EVENT_GET_MODEM_STATUS = 101;
    private static final int EVENT_GET_MODEM_STATUS_DONE = 102;
    private static final int EVENT_GET_PHONE_CAPABILITY_DONE = 103;

    private static PhoneConfigurationManager sInstance = null;
    private final Context mContext;
    private PhoneCapability mStaticCapability;
    private final RadioConfig mRadioConfig;
    private final MainThreadHandler mHandler;
    private final Phone[] mPhones;
    private final Map<Integer, Boolean> mPhoneStatusMap;

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
        //initialize with default, it'll get updated when RADIO is ON/AVAILABLE
        mStaticCapability = getDefaultCapability();
        mRadioConfig = RadioConfig.getInstance(mContext);
        mHandler = new MainThreadHandler();
        mPhoneStatusMap = new HashMap<>();

        notifyCapabilityChanged();

        mPhones = PhoneFactory.getPhones();
        if (!StorageManager.inCryptKeeperBounce()) {
            for (Phone phone : mPhones) {
                phone.mCi.registerForAvailable(mHandler, Phone.EVENT_RADIO_AVAILABLE, phone);
            }
        } else {
            for (Phone phone : mPhones) {
                phone.mCi.registerForOn(mHandler, Phone.EVENT_RADIO_ON, phone);
            }
        }
    }

    private PhoneCapability getDefaultCapability() {
        if (getPhoneCount() > 1) {
            return PhoneConfigurationModels.DSDS_CAPABILITY;
        } else {
            return PhoneConfigurationModels.SSSS_CAPABILITY;
        }
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
            AsyncResult ar;
            Phone phone = null;
            switch (msg.what) {
                case Phone.EVENT_RADIO_AVAILABLE:
                case Phone.EVENT_RADIO_ON:
                    log("Received EVENT_RADIO_AVAILABLE/EVENT_RADIO_ON");
                    if (msg.obj instanceof Phone) {
                        phone = (Phone) msg.obj;
                    }
                    if (phone == null) {
                        log("Unable to add phoneStatus to cache. "
                                + "No phone object provided for event " + msg.what);
                    } else {
                        updatePhoneStatus(phone);
                    }
                    getStaticPhoneCapability();
                    break;
                case EVENT_SWITCH_DSDS_CONFIG_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.exception == null) {
                        int numOfLiveModems = msg.arg1;
                        setMultiSimProperties(numOfLiveModems);
                    } else {
                        log(msg.what + " failure. Not switching multi-sim config." + ar.exception);
                    }
                    break;
                case EVENT_GET_MODEM_STATUS_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.exception == null) {
                        int phoneId = msg.arg1;
                        boolean enabled = (boolean) ar.result;
                        //update the cache each time getModemStatus is requested
                        mPhoneStatusMap.put(phoneId, enabled);
                    } else {
                        log(msg.what + " failure. Not updating modem status." + ar.exception);
                    }
                    break;
                case EVENT_GET_PHONE_CAPABILITY_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.exception == null) {
                        mStaticCapability = (PhoneCapability) ar.result;
                        notifyCapabilityChanged();
                    } else {
                        log(msg.what + " failure. Not getting phone capability." + ar.exception);
                    }
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
        updatePhoneStatus(phone);
    }

    /**
     * Get phone status (enabled/disabled)
     *
     * @param phone which phone to operate on
     */
    public boolean getPhoneStatus(Phone phone) {
        if (phone == null) {
            log("getPhonetatus failed phone is null");
            return false;
        }

        int phoneId = phone.getPhoneId();

        //use cache if the status has already been updated/queried
        if (mPhoneStatusMap.containsKey(phoneId)) {
            return mPhoneStatusMap.get(phoneId);
        } else {
            //return false if modem status is not in cache
            updatePhoneStatus(phone);
            return false;
        }
    }

    /**
     * method to call RIL getModemStatus
     */
    private void updatePhoneStatus(Phone phone) {
        Message callback = Message.obtain(
                mHandler, EVENT_GET_MODEM_STATUS_DONE, phone.getPhoneId(), 0 /**dummy arg*/);
        phone.mCi.getModemStatus(callback);
    }

    /**
     * Add status of the phone to the status HashMap
     * @param phoneId
     * @param status
     */
    public void addToPhoneStatusCache(int phoneId, boolean status) {
        mPhoneStatusMap.put(phoneId, status);
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
    public synchronized PhoneCapability getStaticPhoneCapability() {
        if (getDefaultCapability().equals(mStaticCapability)) {
            log("getStaticPhoneCapability: sending the request for getting PhoneCapability");
            Message callback = Message.obtain(
                    mHandler, EVENT_GET_PHONE_CAPABILITY_DONE);
            mRadioConfig.getPhoneCapability(callback);
        }
        return mStaticCapability;
    }

    /**
     * get configuration related status of each phone.
     */
    public PhoneCapability getCurrentPhoneCapability() {
        return getStaticPhoneCapability();
    }

    public int getNumberOfModemsWithSimultaneousDataConnections() {
        return mStaticCapability.maxActiveData;
    }

    private void notifyCapabilityChanged() {
        PhoneNotifier notifier = new DefaultPhoneNotifier();

        notifier.notifyPhoneCapabilityChanged(mStaticCapability);
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
