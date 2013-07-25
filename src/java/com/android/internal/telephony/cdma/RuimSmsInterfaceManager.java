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
import android.os.Binder;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.IntRangeManager;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;

/**
 * RuimSmsInterfaceManager to provide an inter-process communication to
 * access Sms in Ruim.
 */
public class RuimSmsInterfaceManager extends IccSmsInterfaceManager {
    static final String LOG_TAG = "RuimSmsIM";
    static final boolean DBG = true;

    private CdmaBroadcastRangeManager mCdmaBroadcastRangeManager =
        new CdmaBroadcastRangeManager();

    public RuimSmsInterfaceManager(CDMAPhone phone, SMSDispatcher dispatcher) {
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
        if(DBG) Rlog.d(LOG_TAG, "RuimSmsInterfaceManager finalized");
    }

    @Override
    protected void deleteSms(int index, Message response) {
        mPhone.mCi.deleteSmsOnRuim(index, response);
    }

    @Override
    protected void writeSms(int status, byte[] pdu, byte[] smsc, Message response) {
        //NOTE smsc not used in RUIM
        mPhone.mCi.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu),
                response);
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
                "android.permission.RECEIVE_SMS",
                "Enabling cdma broadcast SMS");

        String client = context.getPackageManager().getNameForUid(
                Binder.getCallingUid());

        if (!mCdmaBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
            log("Failed to add cdma broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);
            return false;
        }

        if (DBG)
            log("Added cdma broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);

        setCdmaBroadcastActivation(!mCdmaBroadcastRangeManager.isEmpty());

        return true;
    }

    @Override
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        if (DBG) log("disableCellBroadcastRange");

        Context context = mPhone.getContext();

        context.enforceCallingPermission(
                "android.permission.RECEIVE_SMS",
                "Disabling cell broadcast SMS");

        String client = context.getPackageManager().getNameForUid(
                Binder.getCallingUid());

        if (!mCdmaBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
            log("Failed to remove cdma broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);
            return false;
        }

        if (DBG)
            log("Removed cdma broadcast subscription for MID range " + startMessageId
                    + " to " + endMessageId + " from client " + client);

        setCdmaBroadcastActivation(!mCdmaBroadcastRangeManager.isEmpty());

        return true;
    }

    class CdmaBroadcastRangeManager extends IntRangeManager {
        private ArrayList<CdmaSmsBroadcastConfigInfo> mConfigList =
                new ArrayList<CdmaSmsBroadcastConfigInfo>();

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
            mConfigList.add(new CdmaSmsBroadcastConfigInfo(startId, endId,
                        1, selected));
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
                CdmaSmsBroadcastConfigInfo[] configs =
                        mConfigList.toArray(new CdmaSmsBroadcastConfigInfo[mConfigList.size()]);
                return setCdmaBroadcastConfig(configs);
            }
        }
    }

    private boolean setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs) {
        if (DBG)
            log("Calling setCdmaBroadcastConfig with " + configs.length + " configurations");

        synchronized (mLock) {
            Message response = mHandler.obtainMessage(EVENT_SET_BROADCAST_CONFIG_DONE);

            mSuccess = false;
            mPhone.mCi.setCdmaBroadcastConfig(configs, response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast config");
            }
        }

        return mSuccess;
    }

    private boolean setCdmaBroadcastActivation(boolean activate) {
        if (DBG)
            log("Calling setCdmaBroadcastActivation(" + activate + ")");

        synchronized (mLock) {
            Message response = mHandler.obtainMessage(EVENT_SET_BROADCAST_ACTIVATION_DONE);

            mSuccess = false;
            mPhone.mCi.setCdmaBroadcastActivation(activate, response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast activation");
            }
        }

        return mSuccess;
    }

    @Override
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[RuimSmsInterfaceManager] " + msg);
    }
}

