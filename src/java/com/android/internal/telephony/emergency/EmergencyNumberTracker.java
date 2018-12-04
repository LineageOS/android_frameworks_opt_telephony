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
import com.android.internal.telephony.LocaleTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.ecc.nano.ProtobufEccData;
import com.android.phone.ecc.nano.ProtobufEccData.EccInfo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import libcore.io.IoUtils;

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

    private static final String EMERGENCY_NUMBER_DB_ASSETS_FILE = "eccdata";

    private List<EmergencyNumber> mEmergencyNumberListFromDatabase = new ArrayList<>();
    private List<EmergencyNumber> mEmergencyNumberListFromRadio = new ArrayList<>();
    private List<EmergencyNumber> mEmergencyNumberList = new ArrayList<>();

    private final LocalLog mEmergencyNumberListDatabaseLocalLog = new LocalLog(20);
    private final LocalLog mEmergencyNumberListRadioLocalLog = new LocalLog(20);
    private final LocalLog mEmergencyNumberListLocalLog = new LocalLog(20);

    /** Event indicating the update for the emergency number list from the radio. */
    private static final int EVENT_UNSOL_EMERGENCY_NUMBER_LIST = 1;
    /**
     * Event indicating the update for the emergency number list from the database due to the
     * change of country code.
     **/
    private static final int EVENT_UPDATE_DB_COUNTRY_ISO_CHANGED = 2;

    public EmergencyNumberTracker(Phone phone, CommandsInterface ci) {
        mPhone = phone;
        mCi = ci;
        initializeDatabaseEmergencyNumberList();
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
                    updateRadioEmergencyNumberListAndNotify((List<EmergencyNumber>) ar.result);
                } else {
                    loge("EVENT_UNSOL_EMERGENCY_NUMBER_LIST: Exception from RIL : "
                            + ar.exception);
                }
                break;
            case EVENT_UPDATE_DB_COUNTRY_ISO_CHANGED:
                if (msg.obj == null) {
                    loge("EVENT_UPDATE_DB_COUNTRY_ISO_CHANGED: Result from UpdateCountryIso is"
                            + " null.");
                } else {
                    updateEmergencyNumberListDatabaseAndNotify((String) msg.obj);
                }
                break;
        }
    }

    private void initializeDatabaseEmergencyNumberList() {
        cacheEmergencyDatabaseByCountry(getInitialCountryIso());
    }

    private String getInitialCountryIso() {
        if (mPhone != null) {
            ServiceStateTracker sst = mPhone.getServiceStateTracker();
            if (sst != null) {
                LocaleTracker lt = sst.getLocaleTracker();
                if (lt != null) {
                    return lt.getCurrentCountry();
                }
            }
        }
        return "";
    }

    /**
     * Update Emergency Number database based on changed Country ISO.
     *
     * @param countryIso
     *
     * @hide
     */
    public void updateEmergencyNumberDatabaseCountryChange(String countryIso) {
        this.obtainMessage(EVENT_UPDATE_DB_COUNTRY_ISO_CHANGED, countryIso).sendToTarget();
    }

    private EmergencyNumber convertEmergencyNumberFromEccInfo(EccInfo eccInfo, String countryIso) {
        String phoneNumber = eccInfo.phoneNumber.trim();
        if (phoneNumber.isEmpty()) {
            loge("EccInfo has empty phone number.");
            return null;
        }
        int emergencyServiceCategoryBitmask = 0;
        for (int typeData : eccInfo.types) {
            switch (typeData) {
                case EccInfo.Type.POLICE:
                    emergencyServiceCategoryBitmask = emergencyServiceCategoryBitmask == 0
                            ? EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                            : emergencyServiceCategoryBitmask
                            | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE;
                    break;
                case EccInfo.Type.AMBULANCE:
                    emergencyServiceCategoryBitmask = emergencyServiceCategoryBitmask == 0
                            ? EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                            : emergencyServiceCategoryBitmask
                            | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE;
                    break;
                case EccInfo.Type.FIRE:
                    emergencyServiceCategoryBitmask = emergencyServiceCategoryBitmask == 0
                            ? EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE
                            : emergencyServiceCategoryBitmask
                            | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE;
                    break;
                default:
                    // Ignores unknown types.
            }
        }
        return new EmergencyNumber(phoneNumber, countryIso, "", emergencyServiceCategoryBitmask,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE);
    }

    private void cacheEmergencyDatabaseByCountry(String countryIso) {
        BufferedInputStream inputStream = null;
        ProtobufEccData.AllInfo allEccMessages = null;
        List<EmergencyNumber> updatedEmergencyNumberList = new ArrayList<>();
        try {
            inputStream = new BufferedInputStream(
                    mPhone.getContext().getAssets().open(EMERGENCY_NUMBER_DB_ASSETS_FILE));
            allEccMessages = ProtobufEccData.AllInfo.parseFrom(readInputStreamToByteArray(
                    new GZIPInputStream(inputStream)));
            logd("Emergency database is loaded. ");
            for (ProtobufEccData.CountryInfo countryEccInfo : allEccMessages.countries) {
                if (countryEccInfo.isoCode.equals(countryIso.toUpperCase())) {
                    for (ProtobufEccData.EccInfo eccInfo : countryEccInfo.eccs) {
                        updatedEmergencyNumberList.add(convertEmergencyNumberFromEccInfo(
                                eccInfo, countryIso));
                    }
                }
            }
            EmergencyNumber.mergeSameNumbersInEmergencyNumberList(updatedEmergencyNumberList);
            mEmergencyNumberListFromDatabase = updatedEmergencyNumberList;
        } catch (IOException ex) {
            loge("Cache emergency database failure: " + ex);
        } finally {
            IoUtils.closeQuietly(inputStream);
        }
    }

    /**
     * Util function to convert inputStream to byte array before parsing proto data.
     */
    private static byte[] readInputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        int size = 16 * 1024; // Read 16k chunks
        byte[] data = new byte[size];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private void updateRadioEmergencyNumberListAndNotify(
            List<EmergencyNumber> emergencyNumberListRadio) {
        Collections.sort(emergencyNumberListRadio);
        logd("updateRadioEmergencyNumberListAndNotify(): receiving " + emergencyNumberListRadio);

        if (!emergencyNumberListRadio.equals(mEmergencyNumberListFromRadio)) {
            try {
                EmergencyNumber.mergeSameNumbersInEmergencyNumberList(emergencyNumberListRadio);
                mEmergencyNumberListFromRadio = emergencyNumberListRadio;
                if (!DBG) {
                    mEmergencyNumberListRadioLocalLog.log("updateRadioEmergencyNumberList:"
                            + emergencyNumberListRadio);
                }
                mergeRadioAndDatabaseList();
                if (!DBG) {
                    mEmergencyNumberListLocalLog.log("updateRadioEmergencyNumberListAndNotify:"
                            + mEmergencyNumberList);
                }
                notifyEmergencyNumberList();
            } catch (NullPointerException ex) {
                loge("updateRadioEmergencyNumberListAndNotify() Phone already destroyed: " + ex
                        + " EmergencyNumberList not notified");
            }
        }
    }

    private void updateEmergencyNumberListDatabaseAndNotify(String countryIso) {
        logd("updateEmergencyNumberListDatabaseAndNotify(): receiving countryIso: "
                + countryIso);

        cacheEmergencyDatabaseByCountry(countryIso);
        if (!DBG) {
            mEmergencyNumberListDatabaseLocalLog.log(
                    "updateEmergencyNumberListDatabaseAndNotify:"
                            + mEmergencyNumberListFromDatabase);
        }
        mergeRadioAndDatabaseList();
        if (!DBG) {
            mEmergencyNumberListLocalLog.log("updateEmergencyNumberListDatabaseAndNotify:"
                    + mEmergencyNumberList);
        }
        notifyEmergencyNumberList();
    }

    private void notifyEmergencyNumberList() {
        try {
            if (getEmergencyNumberList() != null) {
                mPhone.notifyEmergencyNumberList();
                logd("notifyEmergencyNumberList(): notified");
            }
        } catch (NullPointerException ex) {
            loge("notifyEmergencyNumberList(): failure: Phone already destroyed: " + ex);
        }
    }

    /**
     * Merge emergency numbers from the radio and database list, if they are the same emergency
     * numbers.
     */
    private void mergeRadioAndDatabaseList() {
        List<EmergencyNumber> mergedEmergencyNumberList =
                new ArrayList<>(mEmergencyNumberListFromDatabase);
        mergedEmergencyNumberList.addAll(mEmergencyNumberListFromRadio);
        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(mergedEmergencyNumberList);
        Collections.sort(mergedEmergencyNumberList);
        mEmergencyNumberList = mergedEmergencyNumberList;
    }

    /**
     * Get the emergency number list.
     *
     * @return the emergency number list or null if radio indication not support from the HAL
     */
    public List<EmergencyNumber> getEmergencyNumberList() {
        if (!mEmergencyNumberListFromRadio.isEmpty()) {
            return new ArrayList<>(mEmergencyNumberList);
        } else {
            return null;
        }
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
