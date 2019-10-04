/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.CellBroadcastService;
import android.telephony.ICellBroadcastService;
import android.util.LocalLog;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Manages a single binding to the CellBroadcastService from the platform. In mSIM cases callers
 * should have one CellBroadcastServiceManager per phone, and the CellBroadcastServiceManager
 * will handle the single binding.
 */
public class CellBroadcastServiceManager {

    private static final String TAG = "CellBroadcastServiceManager";

    private String mCellBroadcastServicePackage;
    private static CellBroadcastServiceConnection sServiceConnection;
    private Handler mModuleCellBroadcastHandler = null;

    private Phone mPhone;
    private Context mContext;

    private final LocalLog mLocalLog = new LocalLog(100);

    /** New SMS cell broadcast received as an AsyncResult. */
    private static final int EVENT_NEW_SMS_CB = 0;
    private boolean mEnabled;

    public CellBroadcastServiceManager(Context context, Phone phone) {
        Log.d(TAG, "CellBroadcastServiceManager created for phone " + phone.getPhoneId());
        mContext = context;
        mPhone = phone;
    }

    /**
     * Send a CB message to the CellBroadcastServieManager's handler.
     * @param m the message
     */
    public void sendMessageToHandler(Message m) {
        mModuleCellBroadcastHandler.sendMessage(m);
    }

    /**
     * Enable the CB module. The CellBroadcastService will be bound to and CB messages from the
     * RIL will be forwarded to the module.
     */
    public void enable() {
        initCellBroadcastServiceModule();
    }

    /**
     * Disable the CB module. The manager's handler will no longer receive CB messages from the RIL.
     */
    public void disable() {
        mEnabled = false;
        mPhone.mCi.unSetOnNewGsmBroadcastSms(mModuleCellBroadcastHandler);
        mContext.unbindService(sServiceConnection);
    }

    /**
     * The CellBroadcastServiceManager binds to an implementation of the CellBroadcastService
     * specified in com.android.internal.R.string.cellbroadcast_default_package (typically the
     * DefaultCellBroadcastService) and forwards cell broadcast messages to the service.
     */
    private void initCellBroadcastServiceModule() {
        mEnabled = true;
        if (sServiceConnection == null) {
            sServiceConnection = new CellBroadcastServiceConnection();
        }
        mCellBroadcastServicePackage = getCellBroadcastServicePackage();
        if (mCellBroadcastServicePackage != null) {
            mModuleCellBroadcastHandler = new Handler() {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (!mEnabled) {
                        Log.d(TAG, "CB module is disabled.");
                        return;
                    }
                    try {
                        ICellBroadcastService cellBroadcastService =
                                ICellBroadcastService.Stub.asInterface(
                                        sServiceConnection.mService);
                        cellBroadcastService.handleGsmCellBroadcastSms(mPhone.getPhoneId(),
                                (byte[]) ((AsyncResult) msg.obj).result);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to connect to default app: "
                                + mCellBroadcastServicePackage + " err: " + e.toString());
                        mLocalLog.log("Failed to connect to default app: "
                                + mCellBroadcastServicePackage + " err: " + e.toString());
                        mContext.unbindService(sServiceConnection);
                        sServiceConnection = null;
                    }
                }
            };

            Intent intent = new Intent(CellBroadcastService.CELL_BROADCAST_SERVICE_INTERFACE);
            intent.setPackage(mCellBroadcastServicePackage);
            if (sServiceConnection.mService == null) {
                mContext.bindService(intent, sServiceConnection, Context.BIND_AUTO_CREATE);
            }
            mPhone.mCi.setOnNewGsmBroadcastSms(mModuleCellBroadcastHandler, EVENT_NEW_SMS_CB,
                    null);
        } else {
            Log.e(TAG, "Unable to bind service; no cell broadcast service found");
            mLocalLog.log("Unable to bind service; no cell broadcast service found");
        }
    }

    /** Returns the package name of the cell broadcast service, or null if there is none. */
    private String getCellBroadcastServicePackage() {
        return mContext.getResources().getString(
                com.android.internal.R.string.cellbroadcast_default_package);
    }

    private class CellBroadcastServiceConnection implements ServiceConnection {
        IBinder mService;

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "connected to CellBroadcastService");
            this.mService = service;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "mICellBroadcastService has disconnected unexpectedly");
            this.mService = null;
        }
    }

    /**
     * Triggered with `adb shell dumpsys isms`
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CellBroadcastServiceManager:");
        pw.println(" mEnabled=" + mEnabled);
        pw.println(" mCellBroadcastServicePackage=" + mCellBroadcastServicePackage);
        mLocalLog.dump(fd, pw, args);
        pw.flush();
    }
}
