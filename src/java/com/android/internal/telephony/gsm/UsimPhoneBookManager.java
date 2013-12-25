/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements reading and parsing USIM records.
 * Refer to Spec 3GPP TS 31.102 for more details.
 *
 * {@hide}
 */
public class UsimPhoneBookManager extends Handler implements IccConstants {
    private static final String LOG_TAG = "UsimPhoneBookManager";
    private static final boolean DBG = true;
    private PbrFile mPbrFile;
    private Boolean mIsPbrPresent;
    private IccFileHandler mFh;
    private AdnRecordCache mAdnCache;
    private Object mLock = new Object();
    private ArrayList<AdnRecord> mPhoneBookRecords;
    private boolean mEmailPresentInIap = false;
    private int mEmailTagNumberInIap = 0;
    private boolean mAnrPresentInIap = false;
    private int mAnrTagNumberInIap = 0;
    private Map<Integer, ArrayList<byte[]>> mIapFileRecord;
    private Map<Integer, ArrayList<byte[]>> mEmailFileRecord;
    private Map<Integer, ArrayList<byte[]>> mAnrFileRecord;
    private ArrayList<Integer> mAdnLengthList = null;
    private Map<Integer, ArrayList<String>> mEmailsForAdnRec;
    private Map<Integer, ArrayList<String>> mAnrsForAdnRec;
    private int mPendingExtLoads;
    private boolean mSuccess = false;
    private boolean mRefreshCache = false;

    private Map<Integer, ArrayList<Integer>> mAnrFlags;
    private Map<Integer, ArrayList<Integer>> mEmailFlags;
    private ArrayList<Integer>[] mAnrFlagsRecord;
    private ArrayList<Integer>[] mEmailFlagsRecord;

    // Variable used to save valid records' recordnum
    private Map<Integer, ArrayList<Integer>> mRecordNums;

    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;
    private static final int EVENT_ANR_LOAD_DONE = 5;
    private static final int EVENT_EF_EMAIL_RECORD_SIZE_DONE = 6;
    private static final int EVENT_EF_ANR_RECORD_SIZE_DONE = 7;
    private static final int EVENT_UPDATE_EMAIL_RECORD_DONE = 8;
    private static final int EVENT_UPDATE_ANR_RECORD_DONE = 9;
    private static final int EVENT_EF_IAP_RECORD_SIZE_DONE = 10;
    private static final int EVENT_UPDATE_IAP_RECORD_DONE = 11;

    private static final int USIM_TYPE1_TAG   = 0xA8;
    private static final int USIM_TYPE2_TAG   = 0xA9;
    private static final int USIM_TYPE3_TAG   = 0xAA;
    private static final int USIM_EFADN_TAG   = 0xC0;
    private static final int USIM_EFIAP_TAG   = 0xC1;
    private static final int USIM_EFEXT1_TAG  = 0xC2;
    private static final int USIM_EFSNE_TAG   = 0xC3;
    private static final int USIM_EFANR_TAG   = 0xC4;
    private static final int USIM_EFPBC_TAG   = 0xC5;
    private static final int USIM_EFGRP_TAG   = 0xC6;
    private static final int USIM_EFAAS_TAG   = 0xC7;
    private static final int USIM_EFGSD_TAG   = 0xC8;
    private static final int USIM_EFUID_TAG   = 0xC9;
    private static final int USIM_EFEMAIL_TAG = 0xCA;
    private static final int USIM_EFCCP1_TAG  = 0xCB;

    private static final int MAX_NUMBER_SIZE_BYTES = 11;
    private static final int ANR_DESCRIPTION_ID = 0;
    private static final int ANR_BCD_NUMBER_LENGTH = 1;
    private static final int ANR_TON_NPI_ID = 2;
    private static final int ANR_ADDITIONAL_NUMBER_START_ID = 3;
    private static final int ANR_ADDITIONAL_NUMBER_END_ID = 12;
    private static final int ANR_CAPABILITY_ID = 13;
    private static final int ANR_EXTENSION_ID = 14;
    private static final int ANR_ADN_SFI_ID = 15;
    private static final int ANR_ADN_RECORD_IDENTIFIER_ID = 16;

    public UsimPhoneBookManager(IccFileHandler fh, AdnRecordCache cache) {
        mFh = fh;
        mPhoneBookRecords = new ArrayList<AdnRecord>();
        mAdnLengthList = new ArrayList<Integer>();
        mIapFileRecord = new HashMap<Integer, ArrayList<byte[]>>();
        mEmailFileRecord = new HashMap<Integer, ArrayList<byte[]>>();
        mAnrFileRecord = new HashMap<Integer, ArrayList<byte[]>>();
        mRecordNums = new HashMap<Integer, ArrayList<Integer>>();
        mPbrFile = null;

        mAnrFlags = new HashMap<Integer, ArrayList<Integer>>();
        mEmailFlags = new HashMap<Integer, ArrayList<Integer>>();

        // We assume its present, after the first read this is updated.
        // So we don't have to read from UICC if its not present on subsequent reads.
        mIsPbrPresent = true;
        mAdnCache = cache;
    }

    public void reset() {
        if ((mAnrFlagsRecord != null) && (mEmailFlagsRecord != null) && mPbrFile != null) {
            for (int i = 0; i < mPbrFile.mFileIds.size(); i++) {
                mAnrFlagsRecord[i].clear();
                mEmailFlagsRecord[i].clear();
            }
        }
        mAnrFlags.clear();
        mEmailFlags.clear();

        mPhoneBookRecords.clear();
        mIapFileRecord.clear();
        mEmailFileRecord.clear();
        mAnrFileRecord.clear();
        mRecordNums.clear();
        mPbrFile = null;
        mAdnLengthList.clear();
        mIsPbrPresent = true;
        mRefreshCache = false;
    }

    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (mLock) {
            if (!mPhoneBookRecords.isEmpty()) {
                if (mRefreshCache) {
                    mRefreshCache = false;
                    refreshCache();
                }
                return mPhoneBookRecords;
            }

            if (!mIsPbrPresent) return null;

            // Check if the PBR file is present in the cache, if not read it
            // from the USIM.
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }

            if (mPbrFile == null) return null;

            int numRecs = mPbrFile.mFileIds.size();

            if ((mAnrFlagsRecord == null) && (mEmailFlagsRecord == null)) {
                mAnrFlagsRecord = new ArrayList[numRecs];
                mEmailFlagsRecord = new ArrayList[numRecs];
                for (int i = 0; i < numRecs; i++) {
                    mAnrFlagsRecord[i] = new ArrayList<Integer>();
                    mEmailFlagsRecord[i] = new ArrayList<Integer>();
                }
            }

            for (int i = 0; i < numRecs; i++) {
                readAdnFileAndWait(i);
                readEmailFileAndWait(i);
                readAnrFileAndWait(i);
            }
            // All EF files are loaded, post the response.
        }
        return mPhoneBookRecords;
    }

    private void refreshCache() {
        if (mPbrFile == null) return;
        mPhoneBookRecords.clear();

        int numRecs = mPbrFile.mFileIds.size();
        for (int i = 0; i < numRecs; i++) {
            readAdnFileAndWait(i);
        }
    }

    public void invalidateCache() {
        mRefreshCache = true;
    }

    private void readPbrFileAndWait() {
        mFh.loadEFLinearFixedAll(EF_PBR, obtainMessage(EVENT_PBR_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    private void readEmailFileAndWait(int recNum) {
        Map <Integer,Integer> fileIds;
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readEmailFileAndWait");
            return;
        } else {
            fileIds = mPbrFile.mFileIds.get(recNum);
        }
        if (fileIds == null) return;

        if (fileIds.containsKey(USIM_EFEMAIL_TAG)) {
            int efid = fileIds.get(USIM_EFEMAIL_TAG);
            // Check if the EFEmail is a Type 1 file or a type 2 file.
            // If mEmailPresentInIap is true, its a type 2 file.
            // So we read the IAP file and then read the email records.
            // instead of reading directly.
            if (mEmailPresentInIap) {
                readIapFileAndWait(fileIds.get(USIM_EFIAP_TAG), recNum);
                if (!hasRecordIn(mIapFileRecord, recNum)) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
                mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFEMAIL_TAG),
                        obtainMessage(EVENT_EMAIL_LOAD_DONE, recNum));

            } else {
                mFh.loadEFLinearFixedPart(fileIds.get(USIM_EFEMAIL_TAG),
                        getValidRecordNums(recNum), obtainMessage(EVENT_EMAIL_LOAD_DONE, recNum));
            }
            // Read the EFEmail file.
            log("readEmailFileAndWait email efid is : " + fileIds.get(USIM_EFEMAIL_TAG));

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }

            if (!hasRecordIn(mEmailFileRecord, recNum)) {
                Rlog.e(LOG_TAG, "Error: Email file is empty");
                return;
            }
            updatePhoneAdnRecordWithEmail(recNum);
        }

    }

    private void readAnrFileAndWait(int recNum) {
        Map<Integer, Integer> fileIds;
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readAnrFileAndWait");
            return;
        } else {
            fileIds = mPbrFile.mFileIds.get(recNum);
        }
        if (fileIds == null || fileIds.isEmpty())
            return;
        if (fileIds.containsKey(USIM_EFANR_TAG)) {
            int efid = fileIds.get(USIM_EFANR_TAG);
            if (mAnrPresentInIap) {
                readIapFileAndWait(fileIds.get(USIM_EFIAP_TAG), recNum);
                if (!hasRecordIn(mIapFileRecord, recNum)) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
                mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFANR_TAG),
                        obtainMessage(EVENT_ANR_LOAD_DONE, recNum));
            } else {
                mFh.loadEFLinearFixedPart(fileIds.get(USIM_EFANR_TAG), getValidRecordNums(recNum),
                        obtainMessage(EVENT_ANR_LOAD_DONE, recNum));
            }
            log("readAnrFileAndWait anr efid is : " + fileIds.get(USIM_EFANR_TAG));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }
            if (!hasRecordIn(mAnrFileRecord, recNum)) {
                Rlog.e(LOG_TAG, "Error: Anr file is empty");
                return;
            }
            updatePhoneAdnRecordWithAnr(recNum);
        }
    }

    private void readIapFileAndWait(int efid, int recNum) {
        log("pbrIndex is " + recNum + ",iap efid is : " + efid);
        mFh.loadEFLinearFixedPart(efid, getValidRecordNums(recNum),
                obtainMessage(EVENT_IAP_LOAD_DONE, recNum));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    public boolean updateEmailFile(int adnRecNum, String oldEmail, String newEmail) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, USIM_EFEMAIL_TAG);
        if (oldEmail == null)
            oldEmail = "";
        if (newEmail == null)
            newEmail = "";
        String emails = oldEmail + "," + newEmail;
        mSuccess = false;
        if (efid == -1)
            return mSuccess;
        if (mEmailPresentInIap && (TextUtils.isEmpty(oldEmail) && !TextUtils.isEmpty(newEmail))) {
            if (getEmptyEmailNum_Pbrindex(pbrIndex) == 0) {
                log("updateEmailFile getEmptyEmailNum_Pbrindex=0, pbrIndex is " + pbrIndex);
                mSuccess = true;
                return mSuccess;
            }

            mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG);
        } else {
            mSuccess = true;
        }
        if (mSuccess) {
            log("updateEmailFile oldEmail : " + oldEmail + " newEmail:" + newEmail + " emails:"
                    + emails + " efid" + efid + " adnRecNum: " + adnRecNum);
            synchronized (mLock) {
                mFh.getEFLinearRecordSize(efid,
                        obtainMessage(EVENT_EF_EMAIL_RECORD_SIZE_DONE, adnRecNum, efid, emails));
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "interrupted while trying to update by search");
                }
            }
        }
        if (mEmailPresentInIap && mSuccess
                && (!TextUtils.isEmpty(oldEmail) && TextUtils.isEmpty(newEmail))) {
            mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG);
        }
        return mSuccess;
    }

    public boolean updateAnrFile(int adnRecNum, String oldAnr, String newAnr) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, USIM_EFANR_TAG);
        if (oldAnr == null)
            oldAnr = "";
        if (newAnr == null)
            newAnr = "";
        String anrs = oldAnr + "," + newAnr;
        mSuccess = false;
        if (efid == -1)
            return mSuccess;
        if (mAnrPresentInIap && (TextUtils.isEmpty(oldAnr) && !TextUtils.isEmpty(newAnr))) {
            if (getEmptyAnrNum_Pbrindex(pbrIndex) == 0) {
                log("updateAnrFile getEmptyAnrNum_Pbrindex=0, pbrIndex is " + pbrIndex);
                mSuccess = true;
                return mSuccess;
            }

            mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, USIM_EFANR_TAG);
        } else {
            mSuccess = true;
        }
        log("updateAnrFile oldAnr : " + oldAnr + ", newAnr:" + newAnr + " anrs:" + anrs + ", efid"
                + efid + ", adnRecNum: " + adnRecNum);
        synchronized (mLock) {
            mFh.getEFLinearRecordSize(efid,
                    obtainMessage(EVENT_EF_ANR_RECORD_SIZE_DONE, adnRecNum, efid, anrs));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        if (mAnrPresentInIap && mSuccess
                && (!TextUtils.isEmpty(oldAnr) && TextUtils.isEmpty(newAnr))) {
            mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, USIM_EFANR_TAG);
        }
        return mSuccess;
    }

    private boolean updateIapFile(int adnRecNum, String oldValue, String newValue, int tag) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, USIM_EFIAP_TAG);
        mSuccess = false;
        int recordNumber = -1;
        if (efid == -1)
            return mSuccess;
        switch (tag) {
            case USIM_EFEMAIL_TAG:
                recordNumber = getEmailRecNumber(adnRecNum - 1,
                        mPhoneBookRecords.size(), oldValue);
                break;
            case USIM_EFANR_TAG:
                recordNumber = getAnrRecNumber(adnRecNum - 1, mPhoneBookRecords.size(), oldValue);
                break;
        }
        if (TextUtils.isEmpty(newValue)) {
            recordNumber = -1;
        }
        log("updateIapFile  efid=" + efid + ", recordNumber= " + recordNumber + ", adnRecNum="
                + adnRecNum);
        synchronized (mLock) {
            mFh.getEFLinearRecordSize(efid,
                    obtainMessage(EVENT_EF_IAP_RECORD_SIZE_DONE, adnRecNum, recordNumber, tag));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        return mSuccess;
    }

    private int getEfidByTag(int recNum, int tag) {
        Map<Integer, Integer> fileIds;
        int efid = -1;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null)
            return -1;
        if (fileIds.containsKey(tag)) {
            efid = fileIds.get(tag);
        }
        return efid;
    }

    public int getPbrIndexBy(int adnIndex) {
        int len = mAdnLengthList.size();
        int size = 0;
        for (int i = 0; i < len; i++) {
            size += mAdnLengthList.get(i);
            if (adnIndex < size) {
                log("getPbrIndexBy  adnIndex: " + adnIndex + " PbrIndex: " + i);
                return i;
            }
        }
        return -1;
    }

    private int getInitIndexBy(int pbrIndex) {
        int index = 0;
        while (pbrIndex > 0) {
            index += mAdnLengthList.get(pbrIndex - 1);
            pbrIndex--;
        }
        return index;
    }

    private boolean hasRecordIn(Map<Integer, ArrayList<byte[]>> record, int pbrIndex) {
        if (record == null)
            return false;
        try {
            record.get(pbrIndex);
        } catch (IndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "record is empty in pbrIndex" + pbrIndex);
            return false;
        }
        return true;
    }

    private void updatePhoneAdnRecordWithEmail(int pbrIndex) {
        if (!hasRecordIn(mEmailFileRecord, pbrIndex))
            return;
        int numAdnRecs = mAdnLengthList.get(pbrIndex);
        int adnRecIndex;
        if (mEmailPresentInIap && hasRecordIn(mIapFileRecord, pbrIndex)) {
            // The number of records in the IAP file is same as the number of records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the reference file record.
            // i.e value of mEmailTagNumberInIap

            for (int i = 0; i < numAdnRecs; i++) {
                byte[] record = null;
                try {
                    record = mIapFileRecord.get(pbrIndex).get(i);
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    break;
                }
                int recNum = record[mEmailTagNumberInIap];

                if (recNum > 0) {
                    String[] emails = new String[1];
                    // SIM record numbers are 1 based
                    emails[0] = readEmailRecord(recNum - 1, pbrIndex);
                    adnRecIndex = i + getInitIndexBy(pbrIndex);
                    AdnRecord rec = mPhoneBookRecords.get(adnRecIndex);
                    if (rec != null && (!TextUtils.isEmpty(emails[0]))) {
                        rec.setEmails(emails);
                        mPhoneBookRecords.set(adnRecIndex, rec);

                        mEmailFlags.get(pbrIndex).set(recNum - 1, 1);
                    }
                }
            }
        }

        // ICC cards can be made such that they have an IAP file but all
        // records are empty. So we read both type 1 and type 2 file
        // email records, just to be sure.

        int len = mAdnLengthList.get(pbrIndex);
        // Type 1 file, the number of records is the same as the number of
        // records in the ADN file.
        if (mEmailsForAdnRec == null || !mEmailPresentInIap) {
            parseType1EmailFile(len, pbrIndex);
        }
        for (int i = getInitIndexBy(pbrIndex); i < numAdnRecs + getInitIndexBy(pbrIndex); i++) {
            ArrayList<String> emailList = null;
            try {
                emailList = mEmailsForAdnRec.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (emailList == null) continue;

            AdnRecord rec = mPhoneBookRecords.get(i);
            if (rec != null) {
                String[] emails = new String[emailList.size()];
                System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
                rec.setEmails(emails);
                mPhoneBookRecords.set(i, rec);
            }
        }
    }

    private void updatePhoneAdnRecordWithAnr(int pbrIndex) {
        if (!hasRecordIn(mAnrFileRecord, pbrIndex))
            return;
        int numAdnRecs = mAdnLengthList.get(pbrIndex);
        int adnRecIndex;
        if (mAnrPresentInIap && hasRecordIn(mIapFileRecord, pbrIndex)) {
            // The number of records in the IAP file is same as the number of records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the reference file record.
            // i.e value of mAnrTagNumberInIap

            for (int i = 0; i < numAdnRecs; i++) {
                byte[] record = null;
                try {
                    record = mIapFileRecord.get(pbrIndex).get(i);
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    break;
                }
                int recNum = record[mAnrTagNumberInIap];
                if (recNum > 0) {
                    String[] anrs = new String[1];
                    // SIM record numbers are 1 based
                    anrs[0] = readAnrRecord(recNum - 1, pbrIndex);
                    adnRecIndex = i + getInitIndexBy(pbrIndex);
                    AdnRecord rec = mPhoneBookRecords.get(adnRecIndex);
                    if (rec != null && (!TextUtils.isEmpty(anrs[0]))) {
                        rec.setAdditionalNumbers(anrs);
                        mPhoneBookRecords.set(adnRecIndex, rec);
                    }

                }
            }
        }

        // ICC cards can be made such that they have an IAP file but all
        // records are empty. So we read both type 1 and type 2 file
        // anr records, just to be sure.

        // Type 1 file, the number of records is the same as the number of
        // records in the ADN file.
        if (mAnrsForAdnRec == null || !mAnrPresentInIap) {
            parseType1AnrFile(numAdnRecs, pbrIndex);
        }
        for (int i = getInitIndexBy(pbrIndex); i < numAdnRecs + getInitIndexBy(pbrIndex); i++) {
            ArrayList<String> anrList = null;
            try {
                anrList = mAnrsForAdnRec.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (anrList == null)
                continue;
            AdnRecord rec = mPhoneBookRecords.get(i);
            if (rec != null) {
                String[] anrs = new String[anrList.size()];
                System.arraycopy(anrList.toArray(), 0, anrs, 0, anrList.size());
                rec.setAdditionalNumbers(anrs);
                mPhoneBookRecords.set(i, rec);
            }
        }
    }

    void parseType1EmailFile(int numRecs, int pbrIndex) {
        mEmailsForAdnRec = new HashMap<Integer, ArrayList<String>>();
        byte[] emailRec = null;
        int adnRecIndex;
        if (!hasRecordIn(mEmailFileRecord, pbrIndex))
            return;
        for (int i = 0; i < numRecs; i++) {
            try {
                log("parseType1EmailFile: pbrIndex is: " + pbrIndex + ", i is: " + i);
                emailRec = mEmailFileRecord.get(pbrIndex).get(i);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: No email record for ADN, continuing");
                break;
            }
            String email = readEmailRecord(i, pbrIndex);

            if (email == null || email.equals("")) {
                continue;
            }

            int emailRecNum = emailRec[emailRec.length - 1];

            if (mEmailPresentInIap) {
                if (emailRecNum == -1) {
                    continue;
                }
                adnRecIndex = emailRecNum - 1 + getInitIndexBy(pbrIndex);
            } else {
                adnRecIndex = i + getInitIndexBy(pbrIndex);
            }
            // SIM record numbers are 1 based.
            ArrayList<String> val = mEmailsForAdnRec.get(adnRecIndex);
            if (val == null) {
                val = new ArrayList<String>();
            }
            val.add(email);
            // SIM record numbers are 1 based.
            mEmailsForAdnRec.put(adnRecIndex, val);

            mEmailFlags.get(pbrIndex).set(i, 1);
        }
    }

    void parseType1AnrFile(int numRecs, int pbrIndex) {
        mAnrsForAdnRec = new HashMap<Integer, ArrayList<String>>();
        byte[] anrRec = null;
        int adnRecIndex;
        if (!hasRecordIn(mAnrFileRecord, pbrIndex))
            return;
        for (int i = 0; i < numRecs; i++) {
            try {
                anrRec = mAnrFileRecord.get(pbrIndex).get(i);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: No anr record for ADN, continuing");
                break;
            }
            String anr = readAnrRecord(i, pbrIndex);
            if (anr == null || anr.equals("")) {
                continue;
            }
            int anrRecNum = anrRec[anrRec.length - 1];
            if (mAnrPresentInIap) {
                if (anrRecNum == -1) {
                    continue;
                }
                adnRecIndex = anrRecNum - 1 + getInitIndexBy(pbrIndex);
            } else {
                adnRecIndex = i + getInitIndexBy(pbrIndex);
            }
            ArrayList<String> val = mAnrsForAdnRec.get(adnRecIndex);
            if (val == null) {
                val = new ArrayList<String>();
            }
            val.add(anr);
            mAnrsForAdnRec.put(adnRecIndex, val);

            mAnrFlags.get(pbrIndex).set(i, 1);
        }
    }

    private String readEmailRecord(int recNum, int pbrIndex) {
        byte[] emailRec = null;
        if (!hasRecordIn(mEmailFileRecord, pbrIndex))
            return null;
        try {
            emailRec = mEmailFileRecord.get(pbrIndex).get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is X+2 byte, where X bytes is the email address
        String email = IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
        return email;
    }

    private String readAnrRecord(int recNum, int pbrIndex) {
        byte[] anrRec = null;
        if (!hasRecordIn(mAnrFileRecord, pbrIndex))
            return null;
        try {
            anrRec = mAnrFileRecord.get(pbrIndex).get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
        int numberLength = 0xff & anrRec[1];
        if (numberLength > MAX_NUMBER_SIZE_BYTES) {
            log("Invalid number length in anr record");
            return "";
        }
        return PhoneNumberUtils.calledPartyBCDToString(anrRec, 2, numberLength);
    }

    private void readAdnFileAndWait(int recNum) {
        Map <Integer,Integer> fileIds;
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readAdnFileAndWait");
            return;
        } else {
            fileIds = mPbrFile.mFileIds.get(recNum);
        }
        if (fileIds == null || fileIds.isEmpty()) return;


        int extEf = 0;
        // Only call fileIds.get while EFEXT1_TAG is available
        if (fileIds.containsKey(USIM_EFEXT1_TAG)) {
            extEf = fileIds.get(USIM_EFEXT1_TAG);
        }
        log("readAdnFileAndWait adn efid is : " + fileIds.get(USIM_EFADN_TAG));
        mAdnCache.requestLoadAllAdnLike(fileIds.get(USIM_EFADN_TAG), extEf,
                obtainMessage(EVENT_USIM_ADN_LOAD_DONE, recNum));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    private int getEmailRecNumber(int adnRecIndex, int numRecs, String oldEmail) {
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        int recordNumber = -1;
        log("getEmailRecNumber adnRecIndex is: " + adnRecIndex + ", recordIndex is :"
                + recordIndex);
        if (!hasRecordIn(mEmailFileRecord, pbrIndex)) {
            log("getEmailRecNumber recordNumber is: " + recordNumber);
            return recordNumber;
        }
        if (mEmailPresentInIap && hasRecordIn(mIapFileRecord, pbrIndex)) {
            byte[] record = null;
            try {
                record = mIapFileRecord.get(pbrIndex).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getEmailRecNumber");
            }
            if (record != null && record[mEmailTagNumberInIap] > 0) {
                recordNumber = record[mEmailTagNumberInIap];
                log(" getEmailRecNumber: record is " + IccUtils.bytesToHexString(record)
                        + ", the email recordNumber is :" + recordNumber);
                return recordNumber;
            } else {
                int recsSize = mEmailFileRecord.get(pbrIndex).size();
                log("getEmailRecNumber recsSize is: " + recsSize);
                for (int i = 0; i < recsSize; i++) {
                    if ("".equals(oldEmail)) {
                        String emailRecord = readEmailRecord(i, pbrIndex);
                        if (emailRecord != null && emailRecord.equals(oldEmail)) {
                            log("the email record index is :" + (i + 1));
                            return i + 1;
                        }
                    }
                }
            }
        } else {
            recordNumber = recordIndex + 1;
            return recordNumber;
        }
        log("no email record index found");
        return recordNumber;
    }

    private int getAnrRecNumber(int adnRecIndex, int numRecs, String oldAnr) {
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        int recordNumber = -1;
        if (!hasRecordIn(mAnrFileRecord, pbrIndex)) {
            return recordNumber;
        }
        if (mAnrPresentInIap && hasRecordIn(mIapFileRecord, pbrIndex)) {
            byte[] record = null;
            try {
                record = mIapFileRecord.get(pbrIndex).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getAnrRecNumber");
            }
            if (record != null && record[mAnrTagNumberInIap] > 0) {
                recordNumber = record[mAnrTagNumberInIap];
                return recordNumber;
            } else {
                int recsSize = mAnrFileRecord.get(pbrIndex).size();
                if ("".equals(oldAnr)) {
                    for (int i = 0; i < recsSize; i++) {
                        String anrRecord = readAnrRecord(i, pbrIndex);
                        if (anrRecord != null && anrRecord.equals(oldAnr)) {
                            log("the anr record index is :" + (i + 1));
                            return i + 1;
                        }
                    }
                }
            }
        } else {
            recordNumber = recordIndex + 1;
            return recordNumber;
        }
        log("no anr record index found");
        return recordNumber;
    }

    private byte[] buildEmailData(int length, int adnRecIndex, String email) {
        byte[] data = new byte[length];
        for (byte mData : data) {
            mData = (byte) 0xff;
        }
        if (TextUtils.isEmpty(email)) {
            log("[buildEmailData] Empty email record");
            return data; // return the empty record (for delete)
        }
        byte[] byteEmail = GsmAlphabet.stringToGsm8BitPacked(email);
        System.arraycopy(byteEmail, 0, data, 0, byteEmail.length);
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        if (mEmailPresentInIap) {
            data[length - 1] = (byte) (recordIndex + 1);
        }
        log("buildEmailData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    private byte[] buildAnrData(int length, int adnRecIndex, String anr) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) 0xff;
        }
        if (TextUtils.isEmpty(anr)) {
            log("[buildAnrData] Empty anr record");
            return data; // return the empty record (for delete)
        }
        data[ANR_DESCRIPTION_ID] = (byte) (0x0);
        byte[] byteAnr = PhoneNumberUtils.numberToCalledPartyBCD(anr);

        // If the phone number does not matching format, like "+" return null.
        if (byteAnr == null) {
            return null;
        }

        int maxlength = ANR_ADDITIONAL_NUMBER_END_ID - ANR_ADDITIONAL_NUMBER_START_ID + 1;
        if (byteAnr.length > maxlength) {
            System.arraycopy(byteAnr, 0, data, ANR_TON_NPI_ID, maxlength);
            data[ANR_BCD_NUMBER_LENGTH] = (byte) (maxlength);
        } else {
            System.arraycopy(byteAnr, 0, data, ANR_TON_NPI_ID, byteAnr.length);
            data[ANR_BCD_NUMBER_LENGTH] = (byte) (byteAnr.length);
        }
        data[ANR_CAPABILITY_ID] = (byte) 0xFF;
        data[ANR_EXTENSION_ID] = (byte) 0xFF;
        if (length == 17) {
            int pbrIndex = getPbrIndexBy(adnRecIndex);
            int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
            data[ANR_ADN_RECORD_IDENTIFIER_ID] = (byte) (recordIndex + 1);
        }
        log("buildAnrData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    private void createPbrFile(ArrayList<byte[]> records) {
        if (records == null) {
            mPbrFile = null;
            mIsPbrPresent = false;
            return;
        }
        mPbrFile = new PbrFile(records);
    }

    private void putValidRecNums(int pbrIndex) {
        ArrayList<Integer> recordNums = new ArrayList<Integer>();
        int initAdnIndex = getInitIndexBy(pbrIndex);
        log("pbr index is " + pbrIndex + ", initAdnIndex is " + initAdnIndex);
        for (int i = 0; i < mAdnLengthList.get(pbrIndex); i++) {
            recordNums.add(i + 1);
            log("valid recnum is " + (i + 1));
        }
        // Need to read at least one record to inint
        // variable mIapFileRecord, mEmailFileRecord,mAnrFileRecord
        if (recordNums.size() == 0) {
            recordNums.add(1);
        }
        mRecordNums.put(pbrIndex, recordNums);
    }

    private ArrayList<Integer> getValidRecordNums(int pbrIndex) {
        return mRecordNums.get(pbrIndex);
    }

    private boolean hasValidRecords(int pbrIndex) {
        return mRecordNums.get(pbrIndex).size() > 0;
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        byte data[];
        int efid;
        int adnRecIndex;
        int recordIndex;
        int[] recordSize;
        int recordNumber;
        String oldAnr = null;
        String newAnr = null;
        String oldEmail = null;
        String newEmail = null;
        Message response = null;
        int pbrIndex;
        switch (msg.what) {
            case EVENT_PBR_LOAD_DONE:
                log("Loading PBR done");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    createPbrFile((ArrayList<byte[]>) ar.result);
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_USIM_ADN_LOAD_DONE:
                log("Loading USIM ADN records done");
                ar = (AsyncResult) msg.obj;
                pbrIndex = (Integer) ar.userObj;
                if (ar.exception == null) {
                    mPhoneBookRecords.addAll((ArrayList<AdnRecord>) ar.result);
                    mAdnLengthList.add(pbrIndex, ((ArrayList<AdnRecord>) ar.result).size());
                    putValidRecNums(pbrIndex);
                } else {
                    log("can't load USIM ADN records");
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_IAP_LOAD_DONE:
                log("Loading USIM IAP records done");
                ar = (AsyncResult) msg.obj;
                pbrIndex = (Integer) ar.userObj;
                if (ar.exception == null) {
                    mIapFileRecord.put(pbrIndex, (ArrayList<byte[]>) ar.result);
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_EMAIL_LOAD_DONE:
                log("Loading USIM Email records done");
                ar = (AsyncResult) msg.obj;
                pbrIndex = (Integer) ar.userObj;
                if (ar.exception == null) {
                    mEmailFileRecord.put(pbrIndex, (ArrayList<byte[]>) ar.result);

                    log("handlemessage EVENT_EMAIL_LOAD_DONE size is: "
                            + mEmailFileRecord.get(pbrIndex).size());
                    for (int m = 0; m < mEmailFileRecord.get(pbrIndex).size(); m++) {
                        mEmailFlagsRecord[pbrIndex].add(0);
                    }
                    mEmailFlags.put(pbrIndex, mEmailFlagsRecord[pbrIndex]);
                }

                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_ANR_LOAD_DONE:
                log("Loading USIM Anr records done");
                ar = (AsyncResult) msg.obj;
                pbrIndex = (Integer) ar.userObj;
                if (ar.exception == null) {
                    mAnrFileRecord.put(pbrIndex, (ArrayList<byte[]>) ar.result);

                    log("handlemessage EVENT_ANR_LOAD_DONE size is: "
                            + mAnrFileRecord.get(pbrIndex).size());
                    for (int m = 0; m < mAnrFileRecord.get(pbrIndex).size(); m++) {
                        mAnrFlagsRecord[pbrIndex].add(0);
                    }
                    mAnrFlags.put(pbrIndex, mAnrFlagsRecord[pbrIndex]);
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_EF_EMAIL_RECORD_SIZE_DONE:
                log("Loading EF_EMAIL_RECORD_SIZE_DONE");
                ar = (AsyncResult) (msg.obj);
                String emails = (String) (ar.userObj);
                adnRecIndex = ((int) msg.arg1) - 1;
                efid = (int) msg.arg2;
                String email[] = emails.split(",");
                if (email.length == 1) {
                    oldEmail = email[0];
                    newEmail = "";
                } else if (email.length > 1) {
                    oldEmail = email[0];
                    newEmail = email[1];
                }
                if (ar.exception != null) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                recordSize = (int[]) ar.result;
                recordNumber = getEmailRecNumber(adnRecIndex, mPhoneBookRecords.size(), oldEmail);
                if (recordSize.length != 3 || recordNumber > recordSize[2] || recordNumber <= 0) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                data = buildEmailData(recordSize[0], adnRecIndex, newEmail);
                mFh.updateEFLinearFixed(
                        efid,
                        recordNumber,
                        data,
                        null,
                        obtainMessage(EVENT_UPDATE_EMAIL_RECORD_DONE, recordNumber, adnRecIndex,
                                data));
                mPendingExtLoads = 1;
                break;
            case EVENT_EF_ANR_RECORD_SIZE_DONE:
                log("Loading EF_ANR_RECORD_SIZE_DONE");
                ar = (AsyncResult) (msg.obj);
                String anrs = (String) (ar.userObj);
                adnRecIndex = ((int) msg.arg1) - 1;
                efid = (int) msg.arg2;
                String[] anr = anrs.split(",");
                if (anr.length == 1) {
                    oldAnr = anr[0];
                    newAnr = "";
                } else if (anr.length > 1) {
                    oldAnr = anr[0];
                    newAnr = anr[1];
                }
                if (ar.exception != null) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                recordSize = (int[]) ar.result;
                recordNumber = getAnrRecNumber(adnRecIndex, mPhoneBookRecords.size(), oldAnr);
                if (recordSize.length != 3 || recordNumber > recordSize[2] || recordNumber <= 0) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                data = buildAnrData(recordSize[0], adnRecIndex, newAnr);
                if (data == null) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }

                mFh.updateEFLinearFixed(
                        efid,
                        recordNumber,
                        data,
                        null,
                        obtainMessage(EVENT_UPDATE_ANR_RECORD_DONE, recordNumber,
                                adnRecIndex, data));
                mPendingExtLoads = 1;
                break;
            case EVENT_UPDATE_EMAIL_RECORD_DONE:
                log("Loading UPDATE_EMAIL_RECORD_DONE");
                ar = (AsyncResult) (msg.obj);
                if (ar.exception != null) {
                    mSuccess = false;
                }
                data = (byte[]) (ar.userObj);
                recordNumber = (int) msg.arg1;
                adnRecIndex = (int) msg.arg2;
                pbrIndex = getPbrIndexBy(adnRecIndex);
                log("EVENT_UPDATE_EMAIL_RECORD_DONE");
                mPendingExtLoads = 0;
                mSuccess = true;
                mEmailFileRecord.get(pbrIndex).set(recordNumber - 1, data);

                for (int i = 0; i < data.length; i++) {
                    log("EVENT_UPDATE_EMAIL_RECORD_DONE data = " + data[i] + ",i is " + i);
                    if (data[i] != (byte) 0xff) {
                        log("EVENT_UPDATE_EMAIL_RECORD_DONE data !=0xff");
                        mEmailFlags.get(pbrIndex).set(recordNumber - 1, 1);
                        break;
                    }
                    mEmailFlags.get(pbrIndex).set(recordNumber - 1, 0);
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_UPDATE_ANR_RECORD_DONE:
                log("Loading UPDATE_ANR_RECORD_DONE");
                ar = (AsyncResult) (msg.obj);
                data = (byte[]) (ar.userObj);
                recordNumber = (int) msg.arg1;
                adnRecIndex = (int) msg.arg2;
                pbrIndex = getPbrIndexBy(adnRecIndex);
                if (ar.exception != null) {
                    mSuccess = false;
                }
                log("EVENT_UPDATE_ANR_RECORD_DONE");
                mPendingExtLoads = 0;
                mSuccess = true;
                mAnrFileRecord.get(pbrIndex).set(recordNumber - 1, data);

                for (int i = 0; i < data.length; i++) {
                    if (data[i] != (byte) 0xff) {
                        mAnrFlags.get(pbrIndex).set(recordNumber - 1, 1);
                        break;
                    }
                    mAnrFlags.get(pbrIndex).set(recordNumber - 1, 0);
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
            case EVENT_EF_IAP_RECORD_SIZE_DONE:
                log("EVENT_EF_IAP_RECORD_SIZE_DONE");
                ar = (AsyncResult) (msg.obj);
                recordNumber = (int) msg.arg2;
                adnRecIndex = ((int) msg.arg1) - 1;
                pbrIndex = getPbrIndexBy(adnRecIndex);
                efid = getEfidByTag(pbrIndex, USIM_EFIAP_TAG);
                int tag = (Integer) ar.userObj;
                if (ar.exception != null) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                recordSize = (int[]) ar.result;
                data = null;

                recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
                log("handleIAP_RECORD_SIZE_DONE adnRecIndex is: "
                        + adnRecIndex + ", recordNumber is: " + recordNumber
                        + ", recordIndex is: " + recordIndex);
                if (recordSize.length != 3 || recordIndex + 1 > recordSize[2]
                        || recordNumber == 0) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                if (hasRecordIn(mIapFileRecord, pbrIndex)) {
                    data = mIapFileRecord.get(pbrIndex).get(recordIndex);
                    byte[] record_data = new byte[data.length];
                    System.arraycopy(data, 0, record_data, 0, record_data.length);
                    switch (tag) {
                        case USIM_EFEMAIL_TAG:
                            record_data[mEmailTagNumberInIap] = (byte) recordNumber;
                            break;
                        case USIM_EFANR_TAG:
                            record_data[mAnrTagNumberInIap] = (byte) recordNumber;
                            break;
                    }
                    mPendingExtLoads = 1;
                    log(" IAP  efid= " + efid + ", update IAP index= " + (recordIndex)
                            + " with value= " + IccUtils.bytesToHexString(record_data));
                    mFh.updateEFLinearFixed(
                            efid,
                            recordIndex + 1,
                            record_data,
                            null,
                            obtainMessage(EVENT_UPDATE_IAP_RECORD_DONE, adnRecIndex, recordNumber,
                                    record_data));
                }
                break;
            case EVENT_UPDATE_IAP_RECORD_DONE:
                log("EVENT_UPDATE_IAP_RECORD_DONE");
                ar = (AsyncResult) (msg.obj);
                if (ar.exception != null) {
                    mSuccess = false;
                }
                data = (byte[]) (ar.userObj);
                adnRecIndex = (int) msg.arg1;
                pbrIndex = getPbrIndexBy(adnRecIndex);
                recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
                log("handleMessage EVENT_UPDATE_IAP_RECORD_DONE recordIndex is: "
                        + recordIndex + ", adnRecIndex is: " + adnRecIndex);
                mPendingExtLoads = 0;
                mSuccess = true;

                mIapFileRecord.get(pbrIndex).set(recordIndex, data);
                log("the iap email recordNumber is :" + data[mEmailTagNumberInIap]);
                synchronized (mLock) {
                    mLock.notify();
                }
                break;
        }
    }

    private class PbrFile {
        // RecNum <EF Tag, efid>
        HashMap<Integer,Map<Integer,Integer>> mFileIds;

        PbrFile(ArrayList<byte[]> records) {
            mFileIds = new HashMap<Integer, Map<Integer, Integer>>();
            SimTlv recTlv;
            int recNum = 0;
            for (byte[] record: records) {
                recTlv = new SimTlv(record, 0, record.length);
                parseTag(recTlv, recNum);
                recNum ++;
            }
        }

        void parseTag(SimTlv tlv, int recNum) {
            SimTlv tlvEf;
            int tag;
            byte[] data;
            Map<Integer, Integer> val = new HashMap<Integer, Integer>();
            do {
                tag = tlv.getTag();
                switch(tag) {
                case USIM_TYPE1_TAG: // A8
                case USIM_TYPE3_TAG: // AA
                case USIM_TYPE2_TAG: // A9
                    data = tlv.getData();
                    tlvEf = new SimTlv(data, 0, data.length);
                    parseEf(tlvEf, val, tag);
                    break;
                }
            } while (tlv.nextObject());
            mFileIds.put(recNum, val);
        }

        void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag) {
            int tag;
            byte[] data;
            int tagNumberWithinParentTag = 0;
            do {
                tag = tlv.getTag();
                if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
                    mEmailPresentInIap = true;
                    mEmailTagNumberInIap = tagNumberWithinParentTag;
                }
                if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFANR_TAG) {
                    mAnrPresentInIap = true;
                    mAnrTagNumberInIap = tagNumberWithinParentTag;
                }
                switch(tag) {
                    case USIM_EFEMAIL_TAG:
                    case USIM_EFADN_TAG:
                    case USIM_EFEXT1_TAG:
                    case USIM_EFANR_TAG:
                    case USIM_EFPBC_TAG:
                    case USIM_EFGRP_TAG:
                    case USIM_EFAAS_TAG:
                    case USIM_EFGSD_TAG:
                    case USIM_EFUID_TAG:
                    case USIM_EFCCP1_TAG:
                    case USIM_EFIAP_TAG:
                    case USIM_EFSNE_TAG:
                        data = tlv.getData();
                        int efid = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        val.put(tag, efid);
                        break;
                }
                tagNumberWithinParentTag ++;
            } while(tlv.nextObject());
        }
    }

    private void log(String msg) {
        if(DBG) Rlog.d(LOG_TAG, msg);
    }

    public int getAnrCount() {
        int count = 0;
        int pbrIndex = mAnrFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            count += mAnrFlags.get(j).size();
        }
        log("getAnrCount count is" + count);
        return count;
    }

    public int getEmailCount() {
        int count = 0;
        int pbrIndex = mEmailFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            count += mEmailFlags.get(j).size();
        }
        log("getEmailCount count is: " + count);
        return count;
    }

    public int getSpareAnrCount() {
        int count = 0;
        int pbrIndex = mAnrFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            for (int i = 0; i < mAnrFlags.get(j).size(); i++) {
                if (0 == mAnrFlags.get(j).get(i))
                    count++;
            }
        }
        log("getSpareAnrCount count is" + count);
        return count;
    }

    public int getSpareEmailCount() {
        int count = 0;
        int pbrIndex = mEmailFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            for (int i = 0; i < mEmailFlags.get(j).size(); i++) {
                if (0 == mEmailFlags.get(j).get(i))
                    count++;
            }
        }
        log("getSpareEmailCount count is: " + count);
        return count;
    }

    public int getUsimAdnCount() {
        if ((mPhoneBookRecords != null) && (!mPhoneBookRecords.isEmpty())) {
            log("getUsimAdnCount count is" + mPhoneBookRecords.size());
            return mPhoneBookRecords.size();
        } else {
            return 0;
        }
    }

    public int getEmptyEmailNum_Pbrindex(int pbrindex) {
        int count = 0;
        for (int i = 0; i < mEmailFlags.get(pbrindex).size(); i++) {
            if (0 == mEmailFlags.get(pbrindex).get(i))
                count++;
        }
        return count;
    }

    public int getEmptyAnrNum_Pbrindex(int pbrindex) {
        int count = 0;
        for (int i = 0; i < mAnrFlags.get(pbrindex).size(); i++) {
            if (0 == mAnrFlags.get(pbrindex).get(i))
                count++;
        }
        log("getEmptyAnrNum_Pbrindex pbrIndex is: " + pbrindex + " size is: "
                + mEmailFlags.get(pbrindex).size() + ", count is " + count);
        return count;
    }
}
