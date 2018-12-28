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
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.emergency.EmergencyNumber;
import android.text.TextUtils;
import android.util.LocalLog;

import com.android.i18n.phonenumbers.ShortNumberInfo;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.LocaleTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private String mCountryIso;

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
        mCountryIso = getInitialCountryIso().toLowerCase();
        cacheEmergencyDatabaseByCountry(mCountryIso);
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

        mCountryIso = countryIso.toLowerCase();
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
     * @return the emergency number list based on radio indication or ril.ecclist if radio
     *         indication not support from the HAL.
     */
    public List<EmergencyNumber> getEmergencyNumberList() {
        if (!mEmergencyNumberListFromRadio.isEmpty()) {
            return new ArrayList<>(mEmergencyNumberList);
        } else {
            return getEmergencyNumberListFromEccList();
        }
    }

    /**
     * Checks if the number is an emergency number in the current Phone.
     *
     * @return {@code true} if it is; {@code false} otherwise.
     */
    public boolean isEmergencyNumber(String number, boolean exactMatch) {
        if (!mEmergencyNumberListFromRadio.isEmpty()) {
            for (EmergencyNumber num : mEmergencyNumberList) {
                // According to com.android.i18n.phonenumbers.ShortNumberInfo, in
                // these countries, if extra digits are added to an emergency number,
                // it no longer connects to the emergency service.
                Set<String> countriesRequiredForExactMatch = new HashSet<>();
                countriesRequiredForExactMatch.add("br");
                countriesRequiredForExactMatch.add("cl");
                countriesRequiredForExactMatch.add("ni");
                if (exactMatch || countriesRequiredForExactMatch.contains(mCountryIso)) {
                    if (num.getNumber().equals(number)) {
                        return true;
                    }
                } else {
                    if (number.startsWith(num.getNumber())) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            return isEmergencyNumberFromEccList(number, exactMatch);
        }
    }

    /**
     * Get Emergency number list based on EccList. This util is used for solving backward
     * compatibility if device does not support the 1.4 IRadioIndication HAL that reports
     * emergency number list.
     */
    private List<EmergencyNumber> getEmergencyNumberListFromEccList() {
        List<EmergencyNumber> emergencyNumberList = new ArrayList<>();
        int slotId = SubscriptionController.getInstance().getSlotIndex(mPhone.getSubId());

        String ecclist = (slotId <= 0) ? "ril.ecclist" : ("ril.ecclist" + slotId);
        String emergencyNumbers = SystemProperties.get(ecclist, "");
        if (TextUtils.isEmpty(emergencyNumbers)) {
            // then read-only ecclist property since old RIL only uses this
            emergencyNumbers = SystemProperties.get("ro.ril.ecclist");
        }
        if (!TextUtils.isEmpty(emergencyNumbers)) {
            // searches through the comma-separated list for a match,
            // return true if one is found.
            for (String emergencyNum : emergencyNumbers.split(",")) {
                emergencyNumberList.add(new EmergencyNumber(emergencyNum, "", "",
                        EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED, 0));
            }
        }
        emergencyNumbers = ((slotId < 0) ? "112,911,000,08,110,118,119,999" : "112,911");
        for (String emergencyNum : emergencyNumbers.split(",")) {
            emergencyNumberList.add(new EmergencyNumber(emergencyNum, "", "",
                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED, 0));
        }
        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(emergencyNumberList);
        return emergencyNumberList;
    }

    /**
     * Back-up old logics for {@link PhoneNumberUtils#isEmergencyNumberInternal} for legacy
     * and deprecate purpose.
     */
    private boolean isEmergencyNumberFromEccList(String number, boolean useExactMatch) {
        // If the number passed in is null, just return false:
        if (number == null) return false;

        // If the number passed in is a SIP address, return false, since the
        // concept of "emergency numbers" is only meaningful for calls placed
        // over the cell network.
        // (Be sure to do this check *before* calling extractNetworkPortionAlt(),
        // since the whole point of extractNetworkPortionAlt() is to filter out
        // any non-dialable characters (which would turn 'abc911def@example.com'
        // into '911', for example.))
        if (PhoneNumberUtils.isUriNumber(number)) {
            return false;
        }

        // Strip the separators from the number before comparing it
        // to the list.
        number = PhoneNumberUtils.extractNetworkPortionAlt(number);

        String emergencyNumbers = "";
        int slotId = SubscriptionController.getInstance().getSlotIndex(mPhone.getSubId());

        // retrieve the list of emergency numbers
        // check read-write ecclist property first
        String ecclist = (slotId <= 0) ? "ril.ecclist" : ("ril.ecclist" + slotId);

        emergencyNumbers = SystemProperties.get(ecclist, "");

        logd("slotId:" + slotId + " country:" + mCountryIso + " emergencyNumbers: "
                +  emergencyNumbers);

        if (TextUtils.isEmpty(emergencyNumbers)) {
            // then read-only ecclist property since old RIL only uses this
            emergencyNumbers = SystemProperties.get("ro.ril.ecclist");
        }

        if (!TextUtils.isEmpty(emergencyNumbers)) {
            // searches through the comma-separated list for a match,
            // return true if one is found.
            for (String emergencyNum : emergencyNumbers.split(",")) {
                // It is not possible to append additional digits to an emergency number to dial
                // the number in Brazil - it won't connect.
                if (useExactMatch || "br".equalsIgnoreCase(mCountryIso)) {
                    if (number.equals(emergencyNum)) {
                        return true;
                    }
                } else {
                    if (number.startsWith(emergencyNum)) {
                        return true;
                    }
                }
            }
            // no matches found against the list!
            return false;
        }

        logd("System property doesn't provide any emergency numbers."
                + " Use embedded logic for determining ones.");

        // If slot id is invalid, means that there is no sim card.
        // According spec 3GPP TS22.101, the following numbers should be
        // ECC numbers when SIM/USIM is not present.
        emergencyNumbers = ((slotId < 0) ? "112,911,000,08,110,118,119,999" : "112,911");

        for (String emergencyNum : emergencyNumbers.split(",")) {
            if (useExactMatch) {
                if (number.equals(emergencyNum)) {
                    return true;
                }
            } else {
                if (number.startsWith(emergencyNum)) {
                    return true;
                }
            }
        }

        // No ecclist system property, so use our own list.
        if (mCountryIso != null) {
            ShortNumberInfo info = ShortNumberInfo.getInstance();
            if (useExactMatch) {
                return info.isEmergencyNumber(number, mCountryIso.toUpperCase());
            } else {
                return info.connectsToEmergencyNumber(number, mCountryIso.toUpperCase());
            }
        }

        return false;
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
