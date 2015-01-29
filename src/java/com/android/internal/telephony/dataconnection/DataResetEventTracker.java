/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.android.internal.telephony.dataconnection;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.PhoneBase;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;

import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;

public class DataResetEventTracker {

    public static interface ResetEventListener {
        public void onResetEvent();
    }

    private static final boolean DBG = true;

    private DataConnection mDc;
    private TelephonyManager mTelephonyManager = null;
    private GsmCellLocation mPreviousLocation = null;
    private PhoneStateListener mPhoneStateListener = null;
    private Context mContext = null;
    private PhoneBase mPhone = null;
    private ResetEventListener mListener = null;
    private int mPreviousRAT = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case DctConstants.EVENT_DATA_RAT_CHANGED:
                AsyncResult ar = (AsyncResult) msg.obj;
                Pair<Integer, Integer> result = (Pair<Integer, Integer>) ar.result;
                if (result != null) {
                    if (mPreviousRAT > 0 && result.second > 0
                            && mPreviousRAT != result.second) {
                        if (DBG) log("RAT CHANGED, " + mPreviousRAT
                                 + "->" + result.second);
                        notifyResetEvent();
                    }
                    mPreviousRAT = result.second;
                }
                break;
            }
        }
    };

    public DataResetEventTracker(PhoneBase phone, DataConnection dc,
            ResetEventListener listener) {
        mDc = dc;
        if (DBG) log("DataResetEventTracker constructor: " + this);
        mPhone = phone;
        mContext = mPhone.getContext();
        this.mListener = listener;
        mTelephonyManager = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * Register listener for RAU update and RAT change.
     */
    public void startResetEventTracker() {
        if (DBG) log("startResetEventTracker");
        stopResetEventTracker();
        mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                mHandler, DctConstants.EVENT_DATA_RAT_CHANGED, null);

        CellLocation currentCellLocation = mPhone.getCellLocation();
        if (currentCellLocation instanceof GsmCellLocation) {
            mPreviousLocation = (GsmCellLocation) mPhone.getCellLocation();
            if (DBG) log("DataConnection mPreviousLocation : " + mPreviousLocation);
        }
        long subId = SubscriptionManager.getDefaultDataSubId();

        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneStateListener(subId) {
                public void onCellLocationChanged(CellLocation location) {
                    if (DBG) log("DataConnection onCellLocationChanged : "
                                + location);

                    if (location instanceof GsmCellLocation) {
                        GsmCellLocation currentLocation = (GsmCellLocation) location;

                        if (mPreviousLocation != null
                                && currentLocation != null) {
                            if (mPreviousLocation.getCid() != currentLocation
                                    .getCid()
                                    || mPreviousLocation.getLac() != currentLocation
                                            .getLac()) {
                                if (DBG) log("DataConnection location updated");
                                notifyResetEvent();
                            }
                        }
                        mPreviousLocation = currentLocation;
                    }
                }
            };
        }

        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_CELL_LOCATION);
    }

    /**
     * Unregister for RAU update and RAT change.
     */
    public void stopResetEventTracker() {
        if (DBG) log("stopResetTimer");
        try {
            mPreviousRAT = 0;
            mPreviousLocation = null;
            if (mPhoneStateListener != null) {
                mTelephonyManager.listen(mPhoneStateListener,
                        PhoneStateListener.LISTEN_NONE);
            }
            mPhone.getServiceStateTracker()
                    .unregisterForDataRegStateOrRatChanged(mHandler);
        } catch (Exception e) {
            if (DBG) log("error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    public void dispose() {
        if (DBG) log("dispose");
        stopResetEventTracker();
        mTelephonyManager = null;
    }

    /**
     * notify the listener for reset event
     */
    private void notifyResetEvent() {
        stopResetEventTracker();
        if (mListener != null) {
            mListener.onResetEvent();
        }
    }

    private void log(String log) {
        Rlog.d(mDc.getName() + "[DRET]", log);
    }
}
