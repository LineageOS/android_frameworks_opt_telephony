/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.telephony.gsm.UsimPhoneBookManager;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * {@hide}
 */
public final class AdnRecordCache extends Handler implements IccConstants {
    //***** Instance Variables

    private IccFileHandler mFh;
    private UsimPhoneBookManager mUsimPhoneBookManager;

    private int mAdncountofIcc = 0;

    // Indexed by EF ID
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles
        = new SparseArray<ArrayList<AdnRecord>>();

    // People waiting for ADN-like files to be loaded
    SparseArray<ArrayList<Message>> mAdnLikeWaiters
        = new SparseArray<ArrayList<Message>>();

    // People waiting for adn record to be updated
    SparseArray<Message> mUserWriteResponse = new SparseArray<Message>();

    //***** Event Constants

    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;

    // *****USIM TAG Constants
    private static final int USIM_EFANR_TAG   = 0xC4;
    private static final int USIM_EFEMAIL_TAG = 0xCA;
    //***** Constructor



    AdnRecordCache(IccFileHandler fh) {
        mFh = fh;
        mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this);
    }

    //***** Called from SIMRecords

    /**
     * Called from SIMRecords.onRadioNotAvailable and SIMRecords.handleSimRefresh.
     */
    public void reset() {
        mAdnLikeFiles.clear();
        mUsimPhoneBookManager.reset();

        clearWaiters();
        clearUserWriters();

    }

    private void clearWaiters() {
        int size = mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            ArrayList<Message> waiters = mAdnLikeWaiters.valueAt(i);
            AsyncResult ar = new AsyncResult(null, null, new RuntimeException("AdnCache reset"));
            notifyWaiters(waiters, ar);
        }
        mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        mUserWriteResponse.clear();
    }

    /**
     * @return List of AdnRecords for efid if we've already loaded them this
     * radio session, or null if we haven't
     */
    public ArrayList<AdnRecord>
    getRecordsIfLoaded(int efid) {
        return mAdnLikeFiles.get(efid);
    }

    /**
     * Returns extension ef associated with ADN-like EF or -1 if
     * we don't know.
     *
     * See 3GPP TS 51.011 for this mapping
     */
    public int extensionEfForEf(int efid) {
        switch (efid) {
            case EF_MBDN: return EF_EXT6;
            case EF_ADN: return EF_EXT1;
            case EF_SDN: return EF_EXT3;
            case EF_FDN: return EF_EXT2;
            case EF_MSISDN: return EF_EXT1;
            case EF_PBR: return 0; // The EF PBR doesn't have an extension record
            default: return -1;
        }
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    /**
     * Update an ADN-like record in EF by record index
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param adn is the new adn to be stored
     * @param recordIndex is the 1-based adn record index
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2,
            Message response) {

        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        mUserWriteResponse.put(efid, response);

        new AdnRecordLoader(mFh).updateEF(adn, efid, extensionEF,
                recordIndex, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, adn));
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * The ADN-like records must be read through requestLoadAllAdnLike() before
     *
     * @param efid must be one of EF_ADN, EF_FDN, and EF_SDN
     * @param oldAdn is the adn to be replaced
     *        If oldAdn.isEmpty() is ture, it insert the newAdn
     * @param newAdn is the adn to be stored
     *        If newAdn.isEmpty() is true, it delete the oldAdn
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
            String pin2, Message response) {

        int extensionEF;
        extensionEF = extensionEfForEf(efid);

        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }

        ArrayList<AdnRecord> oldAdnList = null;
        try {
            if (efid == EF_PBR) {
                oldAdnList = mUsimPhoneBookManager.loadEfFilesFromUsim();
            } else {
                oldAdnList = getRecordsIfLoaded(efid);
            }
        } catch (NullPointerException e) {
            // NullPointerException will be thrown occasionally when we call this method just
            // during phone changed to airplane mode.
            // Some Object used in this method will be reset, so we add protect code here to avoid
            // phone force close.
            oldAdnList = null;
        }

        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            return;
        }

        int index = -1;
        int count = 1;
        int prePbrIndex = -2;
        int anrNum = 0;
        int emailNum = 0;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
            AdnRecord nextAdnRecord = it.next();
            boolean isEmailOrAnrIsFull = false;
            if (efid == EF_PBR) {
                // There may more than one PBR files in the USIM card, if the current PBR file can
                // not save the new AdnRecord which contain anr or email, try save it into next PBR
                // file.
                int pbrIndex = mUsimPhoneBookManager.getPbrIndexBy(count - 1);
                if (pbrIndex != prePbrIndex) {
                    // For a specific pbrIndex, the anrNum and emailNum is fixed.
                    anrNum = mUsimPhoneBookManager.getEmptyAnrNumPbrIndex(pbrIndex);
                    emailNum = mUsimPhoneBookManager.getEmptyEmailNum_Pbrindex(pbrIndex);
                    prePbrIndex = pbrIndex;
                    Log.d("AdnRecordCache", "updateAdnBySearch, pbrIndex: " + pbrIndex +
                            " anrNum:" + anrNum + " emailNum:" + emailNum);
                }
                if ((anrNum == 0 &&
                        (oldAdn.getAdditionalNumbers() == null &&
                         newAdn.getAdditionalNumbers() != null)) ||
                    (emailNum == 0 &&
                        (oldAdn.getEmails() == null &&
                         newAdn.getEmails() != null))) {
                    isEmailOrAnrIsFull = true;
                }
            }

            if (!isEmailOrAnrIsFull && oldAdn.isEqual(nextAdnRecord)) {
                index = count;
                break;
            }
            count++;
        }

        Log.d("AdnRecordCache", "updateAdnBySearch, update oldADN:" + oldAdn.toString() +
                ", newAdn:" + newAdn.toString() + ",index :" + index);

        if (index == -1) {
            sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            return;
        }

        if (efid == EF_PBR) {
            AdnRecord foundAdn = oldAdnList.get(index-1);
            newAdn.mEfid = foundAdn.mEfid;
            newAdn.mExtRecord = foundAdn.mExtRecord;
            newAdn.mRecordNumber = foundAdn.mRecordNumber;
            // make sure the sequence is same with foundAdn
            oldAdn.setAdditionalNumbers(foundAdn.getAdditionalNumbers());
            oldAdn.setEmails(foundAdn.getEmails());
            newAdn.updateAnrEmailArray(oldAdn,
                    mUsimPhoneBookManager.getEmailFilesCountEachAdn(),
                    mUsimPhoneBookManager.getAnrFilesCountEachAdn());
        }

        Message pendingResponse = mUserWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        if (efid == EF_PBR) {
            updateEmailAndAnr(efid, mUsimPhoneBookManager.getPBPath(),
                    oldAdn, newAdn, index, pin2, response);
        } else {
            mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                    index, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
        }
    }


    /**
     * Responds with exception (in response) if efid is not a known ADN-like
     * record
     */
    public void
    requestLoadAllAdnLike (int efid, int extensionEf, String path, Message response) {
        ArrayList<Message> waiters;
        ArrayList<AdnRecord> result;

        if (efid == EF_PBR) {
            result = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
        }

        // Have we already loaded this efid?
        if (result != null) {
            if (response != null) {
                AsyncResult.forMessage(response).result = result;
                response.sendToTarget();
            }

            return;
        }

        // Have we already *started* loading this efid?

        waiters = mAdnLikeWaiters.get(efid);

        if (waiters != null) {
            // There's a pending request for this EF already
            // just add ourselves to it

            waiters.add(response);
            return;
        }

        // Start loading efid

        waiters = new ArrayList<Message>();
        waiters.add(response);

        mAdnLikeWaiters.put(efid, waiters);


        if (extensionEf < 0) {
            // respond with error if not known ADN-like record

            if (response != null) {
                AsyncResult.forMessage(response).exception
                    = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
            }

            return;
        }

        new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf, path,
                obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));
    }

    //***** Private methods

    private void
    notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {

        if (waiters == null) {
            return;
        }

        for (int i = 0, s = waiters.size() ; i < s ; i++) {
            Message waiter = waiters.get(i);

            AsyncResult.forMessage(waiter, ar.result, ar.exception);
            waiter.sendToTarget();
        }
    }

    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        int efid;

        switch(msg.what) {
            case EVENT_LOAD_ALL_ADN_LIKE_DONE:
                /* arg1 is efid, obj.result is ArrayList<AdnRecord>*/
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                ArrayList<Message> waiters;

                waiters = mAdnLikeWaiters.get(efid);
                mAdnLikeWaiters.delete(efid);

                if (ar.exception == null) {
                    mAdnLikeFiles.put(efid, (ArrayList<AdnRecord>) ar.result);
                }
                notifyWaiters(waiters, ar);
                if (mAdnLikeFiles.get(EF_ADN) != null) {
                    setAdnCount(mAdnLikeFiles.get(EF_ADN).size());
                }
                break;
            case EVENT_UPDATE_ADN_DONE:
                ar = (AsyncResult)msg.obj;
                efid = msg.arg1;
                int index = msg.arg2;
                AdnRecord adn = (AdnRecord) (ar.userObj);

                if (ar.exception == null) {
                    if (mAdnLikeFiles.get(efid) != null) {
                        mAdnLikeFiles.get(efid).set(index - 1, adn);
                    }
                    if (efid == EF_PBR) {
                        mUsimPhoneBookManager.loadEfFilesFromUsim().set(index - 1, adn);
                    }
                }

                Message response = mUserWriteResponse.get(efid);
                mUserWriteResponse.delete(efid);

                // response may be cleared when simrecord is reset,
                // so we should check if it is null.
                if (response != null) {
                    AsyncResult.forMessage(response, null, ar.exception);
                    response.sendToTarget();
                }
                break;
        }

    }

    private void updateEmailAndAnr(int efid, String path, AdnRecord oldAdn,
            AdnRecord newAdn, int index, String pin2, Message response) {
        int extensionEF;
        extensionEF = extensionEfForEf(newAdn.mEfid);
        boolean success = false;
        success = updateUsimRecord(oldAdn, newAdn, index, USIM_EFEMAIL_TAG);

        if (success) {
            success = updateUsimRecord(oldAdn, newAdn, index, USIM_EFANR_TAG);
        } else {
            sendErrorResponse(response, "update email failed");
            return;
        }
        if (success) {
            mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(mFh).updateEF(newAdn, newAdn.mEfid, extensionEF,
                    path, newAdn.mRecordNumber, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
        } else {
            sendErrorResponse(response, "update anr failed");
            return;
        }
    }

    private boolean updateAnrEmailFile(String oldRecord,
                String newRecord, int index, int tag, int efidIndex) {
        boolean success = true;
        try {
            switch (tag) {
                case USIM_EFEMAIL_TAG:
                    success = mUsimPhoneBookManager
                            .updateEmailFile(index, oldRecord, newRecord, efidIndex);
                    break;
                case USIM_EFANR_TAG:
                    success = mUsimPhoneBookManager
                            .updateAnrFile(index, oldRecord, newRecord, efidIndex);
                    break;
                default:
                    success = false;
            }
        } catch (RuntimeException e) {
            success = false;
            Log.e("AdnRecordCache", "update usim record failed", e);
        }

        return success;
    }

    private boolean updateUsimRecord(AdnRecord oldAdn, AdnRecord newAdn, int index, int tag) {
        String[] oldRecords = null;
        String[] newRecords = null;
        String oldRecord = null;
        String newRecord = null;
        boolean success = true;
        // currently we only support one email records
        switch (tag) {
            case USIM_EFEMAIL_TAG:
                oldRecords = oldAdn.getEmails();
                newRecords = newAdn.getEmails();
                break;
            case USIM_EFANR_TAG:
                oldRecords = oldAdn.getAdditionalNumbers();
                newRecords = newAdn.getAdditionalNumbers();
                break;
            default:
                return false;
        }

        if (null == oldRecords && null == newRecords) {
            // UI send empty string, no need to update
            Log.e("AdnRecordCache", "Both old and new EMAIL/ANR are null");
            return true;
        }

        // insert scenario
        if (null == oldRecords && null != newRecords) {
            for (int i = 0; i < newRecords.length; i++) {
                if (!TextUtils.isEmpty(newRecords[i])) {
                    success &= updateAnrEmailFile(null, newRecords[i], index, tag, i);
                }
            }
        // delete scenario
        } else if (null != oldRecords && null == newRecords) {
            for (int i = 0; i < oldRecords.length; i++) {
                if (!TextUtils.isEmpty(oldRecords[i])) {
                    success &= updateAnrEmailFile(oldRecords[i], null, index, tag, i);
                }
            }
        // update scenario
        } else {
            int maxLen = (oldRecords.length > newRecords.length) ?
                            oldRecords.length : newRecords.length;
            for (int i = 0; i < maxLen; i++) {
                oldRecord = (i >= oldRecords.length) ? null : oldRecords[i];
                newRecord = (i >= newRecords.length) ? null : newRecords[i];

                if ((TextUtils.isEmpty(oldRecord) && TextUtils.isEmpty(newRecord)) ||
                    (oldRecord != null && newRecord != null && oldRecord.equals(newRecord))) {
                    continue;
                } else {
                    success &= updateAnrEmailFile(oldRecord, newRecord, index, tag, i);
                }
            }
        }

        return success;
    }

    public void updateUsimAdnByIndex(int efid, AdnRecord newAdn, int recordIndex, String pin2,
            Message response) {

        int extensionEF;
        extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }

        ArrayList<AdnRecord> oldAdnList = null;
        try {
            if (efid == EF_PBR) {
                oldAdnList = mUsimPhoneBookManager.loadEfFilesFromUsim();
            } else {
                oldAdnList = getRecordsIfLoaded(efid);
            }
        } catch (NullPointerException e) {
            // NullPointerException will be thrown occasionally when we call this method just
            // during phone changed to airplane mode.
            // Some Object used in this method will be reset, so we add protect code here to avoid
            // phone force close.
            oldAdnList = null;
        }

        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            return;
        }

        int index = recordIndex;

        if (efid == EF_PBR) {
            AdnRecord foundAdn = oldAdnList.get(index - 1);
            newAdn.mEfid = foundAdn.mEfid;
            newAdn.mExtRecord = foundAdn.mExtRecord;
            newAdn.mRecordNumber = foundAdn.mRecordNumber;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        if (efid == EF_PBR) {
            updateEmailAndAnr(efid, mUsimPhoneBookManager.getPBPath(),
                    oldAdnList.get(index - 1), newAdn, index, pin2, response);
        } else {
            mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF, index, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
        }
    }

    public int getAnrCount() {
        return mUsimPhoneBookManager.getAnrCount();
    }

    public int getEmailCount() {
        return mUsimPhoneBookManager.getEmailCount();
    }
    public int getSpareAnrCount() {
        return mUsimPhoneBookManager.getSpareAnrCount();
    }

    public int getSpareEmailCount() {
        return mUsimPhoneBookManager.getSpareEmailCount();
    }

    public int getAdnCount() {
        return mAdncountofIcc;
    }

    public void setAdnCount(int count) {
        mAdncountofIcc = count;
    }

    public int getUsimAdnCount() {
        return mUsimPhoneBookManager.getUsimAdnCount();
    }
}
