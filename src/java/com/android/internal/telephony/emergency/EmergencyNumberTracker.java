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

package com.android.internal.telephony.emergency;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.emergency.EmergencyNumber;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.ecc.nano.ProtobufEccData;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Emergency Number Tracker that handles update of emergency number list from RIL and emergency
 * number database. This is multi-sim based and each Phone has a EmergencyNumberTracker.
 */
public class EmergencyNumberTracker extends Handler {
    private static final String TAG = EmergencyNumberTracker.class.getSimpleName();

    /** @hide */
    public static boolean DBG = false;

    private final CommandsInterface mCi;
    private final Phone mPhone;
    private List<EmergencyNumber> mEmergencyNumberListFromDatabase = new ArrayList<>();
    private List<EmergencyNumber> mEmergencyNumberListFromRadio = new ArrayList<>();
    private List<EmergencyNumber> mEmergencyNumberList = new ArrayList<>();

    private final LocalLog mEmergencyNumberListDatabaseLocalLog = new LocalLog(20);
    private final LocalLog mEmergencyNumberListRadioLocalLog = new LocalLog(20);
    private final LocalLog mEmergencyNumberListLocalLog = new LocalLog(20);

    /** Event indicating the update for the emergency number list from the radio. */
    private static final int EVENT_UNSOL_EMERGENCY_NUMBER_LIST = 1;

    // TODO EVENT_UPDATE_NETWORK_COUNTRY_ISO

    public EmergencyNumberTracker(Phone phone, CommandsInterface ci) {
        mPhone = phone;
        mCi = ci;
        // TODO cache Emergency Number List Database per country ISO;
        // TODO register for Locale Tracker Country ISO Change
        mCi.registerForEmergencyNumberList(this, EVENT_UNSOL_EMERGENCY_NUMBER_LIST, null);
    }

    /**
     * Message handler for updating emergency number list from RIL, updating emergency number list
     * from database if the country ISO is changed, and notifying the change of emergency number
     * list.
     *
     * @param msg The message
     */
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_UNSOL_EMERGENCY_NUMBER_LIST:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.result == null) {
                    loge("EVENT_UNSOL_EMERGENCY_NUMBER_LIST: Result from RIL is null.");
                } else if ((ar.result != null) && (ar.exception == null)) {
                    updateAndNotifyEmergencyNumberList((List<EmergencyNumber>) ar.result);
                } else {
                    loge("EVENT_UNSOL_EMERGENCY_NUMBER_LIST: Exception from RIL : "
                            + ar.exception);
                }
                break;
        }
    }

    private void updateAndNotifyEmergencyNumberList(
            List<EmergencyNumber> emergencyNumberListRadio) {
        Collections.sort(emergencyNumberListRadio);
        logd("updateAndNotifyEmergencyNumberList(): receiving " + emergencyNumberListRadio);

        if (!emergencyNumberListRadio.equals(mEmergencyNumberListFromRadio)) {
            try {
                mEmergencyNumberListFromRadio = emergencyNumberListRadio;
                if (!DBG) {
                    mEmergencyNumberListRadioLocalLog.log("updateRadioEmergencyNumberList:"
                            + emergencyNumberListRadio);
                }
                List<EmergencyNumber> emergencyNumberListMergedWithDatabase =
                        constructEmergencyNumberListWithDatabase();
                mEmergencyNumberList = emergencyNumberListMergedWithDatabase;
                if (!DBG) {
                    mEmergencyNumberListLocalLog.log("updateEmergencyNumberList:"
                            + emergencyNumberListMergedWithDatabase);
                }
                notifyEmergencyNumberList();
            } catch (NullPointerException ex) {
                loge("updateAndNotifyEmergencyNumberList() Phone already destroyed: " + ex
                        + "EmergencyNumberList not notified");
            }
        }
    }

    private void notifyEmergencyNumberList() {
        List<EmergencyNumber> emergencyNumberListToNotify = getEmergencyNumberList();
        mPhone.notifyEmergencyNumberList(emergencyNumberListToNotify);
        logd("notifyEmergencyNumberList():" + emergencyNumberListToNotify);
    }

    private List<EmergencyNumber> constructEmergencyNumberListWithDatabase() {
        List<EmergencyNumber> emergencyNumberListRadioAndDatabase = mEmergencyNumberListFromRadio;
        // TODO integrate with emergency number database
        // TODO sorting
        return emergencyNumberListRadioAndDatabase;
    }

    public List<EmergencyNumber> getEmergencyNumberList() {
        return new ArrayList<>(mEmergencyNumberList);
    }

    @VisibleForTesting
    public List<EmergencyNumber> getRadioEmergencyNumberList() {
        return new ArrayList<>(mEmergencyNumberListFromRadio);
    }

    private static void logd(String str) {
        Rlog.d(TAG, str);
    }

    private static void loge(String str) {
        Rlog.e(TAG, str);
    }

    /**
     * Dump Emergency Number List info in the tracking
     *
     * @param fd FileDescriptor
     * @param pw PrintWriter
     * @param args args
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println("mEmergencyNumberListDatabaseLocalLog:");
        ipw.increaseIndent();
        mEmergencyNumberListDatabaseLocalLog.dump(fd, pw, args);
        ipw.decreaseIndent();
        ipw.println("   -   -   -   -   -   -   -   -");

        ipw.println("mEmergencyNumberListRadioLocalLog:");
        ipw.increaseIndent();
        mEmergencyNumberListRadioLocalLog.dump(fd, pw, args);
        ipw.decreaseIndent();
        ipw.println("   -   -   -   -   -   -   -   -");

        ipw.println("mEmergencyNumberListLocalLog:");
        ipw.increaseIndent();
        mEmergencyNumberListLocalLog.dump(fd, pw, args);
        ipw.decreaseIndent();

        ipw.flush();
    }
}
