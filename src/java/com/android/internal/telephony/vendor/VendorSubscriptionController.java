/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.vendor;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.Phone;

import java.util.Iterator;
import java.util.List;

/*
 * Extending SubscriptionController here:
 * To implement fall back of sms/data user preferred subId value to next
 * available subId when current preferred SIM deactivated or removed.
 */
public class VendorSubscriptionController extends SubscriptionController {
    static final String LOG_TAG = "VendorSubscriptionController";
    private static final boolean DBG = true;
    private static final boolean VDBG = Rlog.isLoggable(LOG_TAG, Log.VERBOSE);

    private static int sNumPhones;

    private static final int PROVISIONED = 1;
    private static final int NOT_PROVISIONED = 0;

    private TelecomManager mTelecomManager;
    private TelephonyManager mTelephonyManager;

    private RegistrantList mAddSubscriptionRecordRegistrants = new RegistrantList();

    private static final String SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub";

    public static VendorSubscriptionController init(Context c) {
        synchronized (VendorSubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new VendorSubscriptionController(c);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return (VendorSubscriptionController)sInstance;
        }
    }

    public static VendorSubscriptionController getInstance() {
        if (sInstance == null) {
           Log.wtf(LOG_TAG, "getInstance null");
        }

        return (VendorSubscriptionController)sInstance;
    }

    protected VendorSubscriptionController(Context c) {
        super(c);

        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        sNumPhones = TelephonyManager.getDefault().getPhoneCount();
    }

}
