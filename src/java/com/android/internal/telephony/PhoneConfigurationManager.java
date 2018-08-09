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
    private static final String LOG_TAG = "PhoneCfgMgr";

    private static PhoneConfigurationManager sInstance = null;
    private final Context mContext;
    private PhoneCapability mStaticCapability;
    private PhoneCapability mCurrentCapability;

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
        mStaticCapability = telephonyManager.getPhoneCount() == 1
                ? PhoneConfigurationModels.SSSS_CAPABILITY
                : PhoneConfigurationModels.DSDS_CAPABILITY;
        mCurrentCapability = mStaticCapability;

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
     * Enable or disable phone
     *
     * @param phoneId which phone to operate on
     * @param enable true or false
     *
     */
    public void enablePhone(int phoneId, boolean enable) {
        // TODO: send command to modem once interface is ready.
    }

    /**
     * Enable or disable phone
     *
     * @param phoneId which phone to operate on
     * @param enable true or false
     *
     */
    public void enablePhone(int[] phoneId, boolean[] enable) {
        // TODO: send command to modem once interface is ready.
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
     *
     */
    public PhoneCapability getStaticPhoneCapability() {
        return mStaticCapability;
    }

    /**
     * get configuration related status of each phone.
     *
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

    private static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
