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

package com.android.internal.telephony.vendor.dataconnection;

import android.telephony.AccessNetworkConstants;
import android.telephony.Rlog;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccRecords;

import java.util.HashSet;
import java.util.Iterator;

public class VendorDcTracker extends DcTracker {
    private String LOG_TAG = "VendorDCT";
    private static final boolean DBG = true;
    private HashSet<String> mIccidSet = new HashSet<String>();
    private int mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;

    // Constructor
    public VendorDcTracker(Phone phone, int transportType) {
        super(phone, transportType);
        mTransportType = transportType;
        LOG_TAG = LOG_TAG + "-" +
                ((transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) ? "C" : "I");
        if (DBG) log(LOG_TAG + ".constructor");
        fillIccIdSet();
    }

    protected boolean isRecordsLoaded() {
        boolean recordsLoaded = false;

        IccRecords r = mIccRecords.get();
        if (r != null) {
            recordsLoaded = r.getRecordsLoaded();
        }

        return recordsLoaded;
    }

    @Override
    protected void onRecordsLoadedOrSubIdChanged() {
        if (DBG) log("onRecordsLoaded: createAllApnList");
        // Just support auto attach for WWAN only
        if (mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
            mAutoAttachOnCreationConfig = mPhone.getContext().getResources()
                    .getBoolean(com.android.internal.R.bool.config_auto_attach_data_on_creation);
        }

        createAllApnList();
        setDataProfilesAsNeeded();
        // Send initial attach apn only if sim records are loaded
        if (isRecordsLoaded()) {
            setInitialAttachApn();
        }
        mPhone.notifyAllActiveDataConnections();
        setupDataOnAllConnectableApns(Phone.REASON_SIM_LOADED, RetryFailures.ALWAYS);
    }

    @Override
    protected boolean allowInitialAttachForOperator() {
        IccRecords r = mIccRecords.get();
        String iccId = (r != null) ? r.getIccId() : "";
        if (iccId != null) {
            Iterator<String> itr = mIccidSet.iterator();
            while (itr.hasNext()) {
                if (iccId.contains(itr.next())) {
                    return false;
                }
            }
        }
        return true;
    }

    // Support added to allow initial attach request with only default for a Carrier
    protected void fillIccIdSet() {
        mIccidSet.add("8991840");
        mIccidSet.add("8991854");
        mIccidSet.add("8991855");
        mIccidSet.add("8991856");
        mIccidSet.add("8991857");
        mIccidSet.add("8991858");
        mIccidSet.add("8991859");
        mIccidSet.add("899186");
        mIccidSet.add("8991870");
        mIccidSet.add("8991871");
        mIccidSet.add("8991872");
        mIccidSet.add("8991873");
        mIccidSet.add("8991874");
    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }
}
