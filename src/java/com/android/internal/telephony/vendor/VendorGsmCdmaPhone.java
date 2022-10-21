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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.TelephonyComponentFactory;

public class VendorGsmCdmaPhone extends GsmCdmaPhone {
    private static final String LOG_TAG = "VendorGsmCdmaPhone";
    private static final int PROP_EVENT_START = EVENT_LAST + 100;
    protected static final int EVENT_SUBINFO_RECORD_ADDED = PROP_EVENT_START + 1;

    public VendorGsmCdmaPhone(Context context,
            CommandsInterface ci, PhoneNotifier notifier, int phoneId,
            int precisePhoneType, TelephonyComponentFactory telephonyComponentFactory) {
        this(context, ci, notifier, false, phoneId, precisePhoneType,
                telephonyComponentFactory);
    }

    public VendorGsmCdmaPhone(Context context,
            CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int phoneId,
            int precisePhoneType, TelephonyComponentFactory telephonyComponentFactory) {
        super(context, ci, notifier, unitTestMode, phoneId, precisePhoneType,
                telephonyComponentFactory);
        Rlog.d(LOG_TAG, "Constructor");

        VendorSubscriptionController.getInstance().registerForAddSubscriptionRecord(this,
                EVENT_SUBINFO_RECORD_ADDED, null);
    }

    @Override
    protected void phoneObjectUpdater(int newVoiceTech) {
        super.phoneObjectUpdater(newVoiceTech);
    }

    @Override
    public void handleMessage(Message msg) {
        Rlog.d(LOG_TAG, "handleMessage: Event: " + msg.what);
        AsyncResult ar;
        switch(msg.what) {

            case EVENT_SUBINFO_RECORD_ADDED:
                ar = (AsyncResult) msg.obj;
                if (ar != null && ar.result != null) {
                    int phoneId = (Integer) ar.result;
                    if (phoneId == getPhoneId()) {
                        // When SIM hot-swap performed, this is triggering point to
                        // initiate SIM ON/OFF request if required.
                        reapplyUiccAppsEnablementIfNeeded(ENABLE_UICC_APPS_MAX_RETRIES);
                     }
                }
                break;

            case EVENT_GET_RADIO_CAPABILITY:
                super.handleMessage(msg);
                VendorSubscriptionController.getInstance().notifyRadioCapabilityAvailable();
                break;

            default: {
                super.handleMessage(msg);
            }

        }
    }
}
