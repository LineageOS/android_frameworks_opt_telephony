/*
 * Copyright (C) 2008 The Android Open Source Project
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


package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.uicc.IccUtils;

/**
 * RuimSmsInterfaceManager to provide an inter-process communication to
 * access Sms in Ruim.
 */
public class RuimSmsInterfaceManager extends IccSmsInterfaceManager {
    static final String LOG_TAG = "CDMA";
    static final boolean DBG = true;

    public RuimSmsInterfaceManager(CDMAPhone phone, SMSDispatcher dispatcher) {
        super(phone);
        mDispatcher = dispatcher;
    }

    public void dispose() {
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Rlog.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        if(DBG) Rlog.d(LOG_TAG, "RuimSmsInterfaceManager finalized");
    }

    protected void deleteSms(int index, Message response) {
        mPhone.mCM.deleteSmsOnRuim(index, response);
    }

    protected void writeSms(int status, byte[] pdu, byte[] smsc, Message response) {
        //NOTE smsc not used in RUIM
        mPhone.mCM.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu),
                response);
    }

    public boolean enableCellBroadcast(int messageIdentifier) {
        // Not implemented
        Rlog.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    public boolean disableCellBroadcast(int messageIdentifier) {
        // Not implemented
        Rlog.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        // Not implemented
        Rlog.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        // Not implemented
        Rlog.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[RuimSmsInterfaceManager] " + msg);
    }
}

