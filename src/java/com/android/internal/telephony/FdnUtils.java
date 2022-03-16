/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.Locale;

/**
 * This is a basic utility class for common functions related to Fixed Dialing Numbers
 * designed as per 3GPP 22.101.
 */
public class FdnUtils {
    private static final boolean VDBG = false;
    private static final String LOG_TAG = FdnUtils.class.getSimpleName();

    /**
     * The following function checks if dialed number is blocked due to FDN.
     *
     * @param phoneId The phone object id for which the FDN check is performed
     * @param dialStr dialed phone number
     * @param defaultCountryIso country ISO for the subscription associated with this phone
     * @return true if dialStr is blocked due to FDN check.
     */
    public static boolean isNumberBlockedByFDN(int phoneId, String dialStr,
            String defaultCountryIso) {
        final UiccCardApplication app = getUiccCardApplication(phoneId);

        if (!isFdnEnabled(app)) {
            return false;
        }

        final ArrayList<AdnRecord> fdnList = getFdnList(app);
        return !isFDN(dialStr, defaultCountryIso, fdnList);
    }

    /**
     * The following function checks if destination address or smsc is blocked due to FDN.
     * @param context context
     * @param subId subscription ID
     * @param destAddr destination address of the message
     * @param smscAddr smsc address of the subscription
     * @return true if either destAddr or smscAddr is blocked due to FDN.
     */
    public static boolean isNumberBlockedByFDN(Context context, int subId, String destAddr,
            String smscAddr) {
        // Skip FDN check for emergency numbers
        final TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        if(tm.isEmergencyNumber(destAddr)) {
            return false;
        }

        final int phoneId = SubscriptionManager.getPhoneId(subId);
        final String defaultCountryIso = tm.getSimCountryIso().toUpperCase(Locale.ENGLISH);
        return (isNumberBlockedByFDN(phoneId, smscAddr, defaultCountryIso) ||
                isNumberBlockedByFDN(phoneId, destAddr, defaultCountryIso));
    }

    /**
     * Checks if dialStr is part of FDN list.
     *
     * @param fdnList List of all FDN records associated with a sim card
     * @param dialStr dialed phone number
     * @param defaultCountryIso country ISO for the subscription associated with this phone
     * @return true if dialStr is present in the fdnList.
     */
    @VisibleForTesting
    public static boolean isFDN(String dialStr, String defaultCountryIso,
            ArrayList<AdnRecord> fdnList) {
        if (fdnList == null || fdnList.isEmpty() || TextUtils.isEmpty(dialStr)) {
            Rlog.w(LOG_TAG, "isFDN: unexpected null value");
            return false;
        }

        // Parse the dialStr and convert it to E164 format
        String dialStrE164 = null;
        String dialStrNational = null;
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            final PhoneNumber phoneNumber = phoneNumberUtil.parse(dialStr, defaultCountryIso);
            dialStrE164 = phoneNumberUtil.format(phoneNumber, PhoneNumberFormat.E164);
            dialStrNational = String.valueOf(phoneNumber.getNationalNumber());
        } catch (NumberParseException ignored) {
            Rlog.w(LOG_TAG, "isFDN: could not parse dialStr");
        }

        /**
         * Returns true if dialStrE164 or dialStrNational or dialStr starts with fdnNumber
         * E.g.1: returns true if fdnNumber="123" and dialStr="12345"
         * E.g.2: does not return true if fdnNumber="1123" and dialStr="12345"
         */
        for (AdnRecord fdn: fdnList) {
            String fdnNumber = fdn.getNumber();
            if (TextUtils.isEmpty(fdnNumber)) {
                continue;
            }

            if(!TextUtils.isEmpty(dialStrE164)) {
                if(dialStrE164.startsWith(fdnNumber)) {
                    return true;
                }
            }

            if(!TextUtils.isEmpty(dialStrNational)) {
                if (dialStrNational.startsWith(fdnNumber)) {
                    return true;
                }
            }

            if (dialStr.startsWith(fdnNumber)) {
                return true;
            }
        }

        if (VDBG) {
            Rlog.i(LOG_TAG, "isFDN: dialed number not present in FDN list");
        }
        return false;
    }

    private static ArrayList<AdnRecord> getFdnList(UiccCardApplication app) {
        if (app == null) {
            return null;
        }

        final IccRecords iccRecords = app.getIccRecords();
        if (iccRecords == null) {
            return null;
        }

        final AdnRecordCache adnRecordCache = iccRecords.getAdnCache();
        if(adnRecordCache == null) {
            return null;
        }

        return adnRecordCache.getRecordsIfLoaded(IccConstants.EF_FDN);
    }

    private static boolean isFdnEnabled(UiccCardApplication app) {
        if (app == null) {
            return false;
        }

        if (!app.getIccFdnAvailable()) {
            return false;
        }

        return app.getIccFdnEnabled();
    }

    private static UiccCardApplication getUiccCardApplication(int phoneId) {
        final UiccProfile uiccProfile = UiccController.getInstance()
                .getUiccProfileForPhone(phoneId);
        if (uiccProfile == null) {
            return null;
        }

        return uiccProfile.getApplication(UiccController.APP_FAM_3GPP);
    }
}