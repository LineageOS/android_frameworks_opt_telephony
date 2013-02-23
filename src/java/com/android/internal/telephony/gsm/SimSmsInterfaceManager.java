/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony.gsm;

import android.Manifest;
import android.content.Context;
import android.os.Binder;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.IntRangeManager;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;

/**
 * SimSmsInterfaceManager to provide an inter-process communication to
 * access Sms in Sim.
 */
public class SimSmsInterfaceManager extends IccSmsInterfaceManager {
    static final String LOG_TAG = "SimSmsIM";
    static final boolean DBG = true;

    private CellBroadcastRangeManager mCellBroadcastRangeManager =
            new CellBroadcastRangeManager();

    private static final int SMS_CB_CODE_SCHEME_MIN = 0;
    private static final int SMS_CB_CODE_SCHEME_MAX = 255;

    public SimSmsInterfaceManager(GSMPhone phone, SMSDispatcher dispatcher) {
        super(phone);
        mDispatcher = dispatcher;
    }

    public void dispose() {
    }

    @Override
    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Rlog.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        if(DBG) Rlog.d(LOG_TAG, "SimSmsInterfaceManager finalized");
    }

    @Override
    protected void deleteSms(int index, Message response) {
        mPhone.mCi.deleteSmsOnSim(index, response);
    }

    @Override
    protected void writeSms(int status, byte[] pdu, byte[] smsc, Message response) {
        mPhone.mCi.writeSmsToSim(status, IccUtils.bytesToHexString(smsc),
                IccUtils.bytesToHexString(pdu), response);
    }

    @Override
    public boolean enableCellBroadcast(int messageIdentifier) {
        return enableCellBroadcastRange(messageIdentifier, messageIdentifier);
    }

    @Override
    public boolean disableCellBroadcast(int messageIdentifier) {
        return disableCellBroadcastRange(messageIdentifier, messageIdentifier);
    }

    @Override
    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        if (DBG) log("enableCellBroadcastRange");

        Context context = mPhone.getContext();

        context.enforceCallingPermission(
                Manifest.permission.RECEIVE_SMS,
                "Enabling cell broadcast SMS");

        String client = context.getPackageManager().getNameForUid(
                Binder.getCallingUid());

        if (!mCellBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
            log("Failed to add cell broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);
            return false;
        }

        if (DBG)
            log("Added cell broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);

        setCellBroadcastActivation(!mCellBroadcastRangeManager.isEmpty());

        return true;
    }

    @Override
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        if (DBG) log("disableCellBroadcastRange");

        Context context = mPhone.getContext();

        context.enforceCallingPermission(
                Manifest.permission.RECEIVE_SMS,
                "Disabling cell broadcast SMS");

        String client = context.getPackageManager().getNameForUid(
                Binder.getCallingUid());

        if (!mCellBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
            log("Failed to remove cell broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);
            return false;
        }

        if (DBG)
            log("Removed cell broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);

        setCellBroadcastActivation(!mCellBroadcastRangeManager.isEmpty());

        return true;
    }

    class CellBroadcastRangeManager extends IntRangeManager {
        private ArrayList<SmsBroadcastConfigInfo> mConfigList =
                new ArrayList<SmsBroadcastConfigInfo>();

        /**
         * Called when the list of enabled ranges has changed. This will be
         * followed by zero or more calls to {@link #addRange} followed by
         * a call to {@link #finishUpdate}.
         */
        @Override
        protected void startUpdate() {
            mConfigList.clear();
        }

        /**
         * Called after {@link #startUpdate} to indicate a range of enabled
         * values.
         * @param startId the first id included in the range
         * @param endId the last id included in the range
         */
        @Override
        protected void addRange(int startId, int endId, boolean selected) {
            mConfigList.add(new SmsBroadcastConfigInfo(startId, endId,
                        SMS_CB_CODE_SCHEME_MIN, SMS_CB_CODE_SCHEME_MAX, selected));
        }

        /**
         * Called to indicate the end of a range update started by the
         * previous call to {@link #startUpdate}.
         * @return true if successful, false otherwise
         */
        @Override
        protected boolean finishUpdate() {
            if (mConfigList.isEmpty()) {
                return true;
            } else {
                SmsBroadcastConfigInfo[] configs =
                        mConfigList.toArray(new SmsBroadcastConfigInfo[mConfigList.size()]);
                return setCellBroadcastConfig(configs);
            }
        }
    }

    private boolean setCellBroadcastConfig(SmsBroadcastConfigInfo[] configs) {
        if (DBG)
            log("Calling setGsmBroadcastConfig with " + configs.length + " configurations");

        synchronized (mLock) {
            Message response = mHandler.obtainMessage(EVENT_SET_BROADCAST_CONFIG_DONE);

            mSuccess = false;
            mPhone.mCi.setGsmBroadcastConfig(configs, response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast config");
            }
        }

        return mSuccess;
    }

    private boolean setCellBroadcastActivation(boolean activate) {
        if (DBG)
            log("Calling setCellBroadcastActivation(" + activate + ')');

        synchronized (mLock) {
            Message response = mHandler.obtainMessage(EVENT_SET_BROADCAST_ACTIVATION_DONE);

            mSuccess = false;
            mPhone.mCi.setGsmBroadcastActivation(activate, response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast activation");
            }
        }

        return mSuccess;
    }

    @Override
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[SimSmsInterfaceManager] " + msg);
    }
}
