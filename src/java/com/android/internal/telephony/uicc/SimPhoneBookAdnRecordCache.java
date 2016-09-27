/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
         copyright notice, this list of conditions and the following
         disclaimer in the documentation and/or other materials provided
         with the distribution.
 * Neither the name of The Linux Foundation, Inc. nor the names of its
         contributors may be used to endorse or promote products derived
         from this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
   WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
   ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
   BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
   CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
   SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
   BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
   WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
   OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
   IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.android.internal.telephony.uicc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * {@hide}
 */
public final class SimPhoneBookAdnRecordCache extends Handler {
    //***** Instance Variables
    private static final String LOG_TAG = "SimPhoneBookAdnRecordCache";
    private static final boolean DBG = true;
    // member variables
    protected final CommandsInterface mCi;
    protected int mPhoneId;
    protected Context mContext;
    //max adn count
    private int mAdnCount = 0;
    //valid adn count
    private int mValidAdnCount = 0;
    private int mEmailCount = 0;
    private int mValidEmailCount = 0;
    private int mAddNumCount = 0;
    private int mValidAddNumCount = 0;
    private int mRecCount = 0;
    private Object mLock = new Object();
    private ArrayList<AdnRecord> mSimPbRecords;
    private boolean mRefreshAdnCache = false;

    // People waiting for ADN-like files to be loaded
    ArrayList<Message> mAdnLoadingWaiters = new ArrayList<Message>();

    // People waiting for adn record to be updated
    Message mAdnUpdatingWaiter = null;

    //EXT file Used/free records.
    SparseArray<int[]> extRecList = new SparseArray<int[]>();

    //***** Event Constants
    static final int EVENT_INIT_ADN_DONE = 1;
    static final int EVENT_QUERY_ADN_RECORD_DONE = 2;
    static final int EVENT_LOAD_ADN_RECORD_DONE = 3;
    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 4;
    static final int EVENT_UPDATE_ADN_RECORD_DONE = 5;
    static final int EVENT_SIM_REFRESH = 6;

    static final String ACTION_ADN_INIT_DONE = "android.intent.action.ACTION_ADN_INIT_DONE";

    //***** Constructor
    public SimPhoneBookAdnRecordCache(Context context,  int phoneId,  CommandsInterface ci) {
        mCi = ci;
        mSimPbRecords = new ArrayList<AdnRecord>();
        mPhoneId = phoneId;
        mContext = context;
        mCi.registerForAdnInitDone(this, EVENT_INIT_ADN_DONE, null);
        mCi.registerForIccRefresh(this, EVENT_SIM_REFRESH, null);

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        context.registerReceiver(sReceiver, intentFilter);
    }

    public void reset() {
        mAdnLoadingWaiters.clear();
        clearUpdatingWriter();

        mSimPbRecords.clear();
        mRecCount = 0;
        mRefreshAdnCache = false;
    }

    private void clearUpdatingWriter() {
        sendErrorResponse(mAdnUpdatingWaiter, "SimPhoneBookAdnRecordCache reset");
        mAdnUpdatingWaiter = null;
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    private void
    notifyAndClearWaiters() {
        if (mAdnLoadingWaiters == null) {
            return;
        }

        for (int i = 0, s = mAdnLoadingWaiters.size() ; i < s ; i++) {
            Message response = mAdnLoadingWaiters.get(i);

            if ((response != null)) {
                AsyncResult.forMessage(response).result = mSimPbRecords;
                response.sendToTarget();
            }
        }

        mAdnLoadingWaiters.clear();
    }

    public void queryAdnRecord () {
        mRecCount = 0;
        mAdnCount = 0;
        mValidAdnCount = 0;
        mEmailCount = 0;
        mAddNumCount = 0;

        log("start to queryAdnRecord");

        mCi.getAdnRecord(obtainMessage(EVENT_QUERY_ADN_RECORD_DONE));
        mCi.registerForAdnRecordsInfo(this, EVENT_LOAD_ADN_RECORD_DONE, null);

        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in queryAdnRecord");
        }

        mCi.unregisterForAdnRecordsInfo(this);
    }


    public void
    requestLoadAllAdnLike(Message response) {
        if (mAdnLoadingWaiters != null) {
            mAdnLoadingWaiters.add(response);
        }

        synchronized (mLock) {
            if (!mSimPbRecords.isEmpty()) {
                log("ADN cache has already filled in");
                if (mRefreshAdnCache) {
                    mRefreshAdnCache = false;
                    refreshAdnCache();
                } else {
                    notifyAndClearWaiters();
                }

                return;
            }

            queryAdnRecord();
        }
    }

    public void updateSimPbAdnBySearch(AdnRecord oldAdn, AdnRecord newAdn, Message response) {
        ArrayList<AdnRecord> oldAdnList = mSimPbRecords;

        synchronized (mLock) {
            if (!mSimPbRecords.isEmpty()) {
                log("ADN cache has already filled in");
                if (mRefreshAdnCache) {
                    mRefreshAdnCache = false;
                    refreshAdnCache();
                }
            } else {
                queryAdnRecord();
            }
        }

        if (oldAdnList == null) {
            sendErrorResponse(response, "Sim PhoneBook Adn list not exist");
            return;
        }

        int index = -1;
        int count = 1;
        if (oldAdn.isEmpty() && !newAdn.isEmpty()) {
            //add contact
            index = 0;
        } else {
            //delete or update contact
            for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
                if (oldAdn.isEqual(it.next())) {
                    index = count;
                    break;
                }
                count++;
            }
        }
        if (index == -1) {
            sendErrorResponse(response, "Sim PhoneBook Adn record don't exist for " + oldAdn);
            return;
        }

        if(index == 0 && mValidAdnCount == mAdnCount) {
            sendErrorResponse(response, "Sim PhoneBook Adn record is full");
            return;
        }

        int recordIndex = (index == 0) ? 0 : oldAdnList.get(index-1).getRecordNumber();

        SimPhoneBookAdnRecord updateAdn = new SimPhoneBookAdnRecord();
        updateAdn.mRecordIndex = recordIndex;
        updateAdn.mAlphaTag = newAdn.getAlphaTag();
        updateAdn.mNumber = newAdn.getNumber();
        if(newAdn.getEmails() != null) {
            updateAdn.mEmails = newAdn.getEmails();
            updateAdn.mEmailCount = updateAdn.mEmails.length;
        }
        if(newAdn.getAdditionalNumbers() != null) {
            updateAdn.mAdNumbers = newAdn.getAdditionalNumbers();
            updateAdn.mAdNumCount = updateAdn.mAdNumbers.length;
        }

        if (mAdnUpdatingWaiter != null) {
            sendErrorResponse(response, "Have pending update for Sim PhoneBook Adn");
            return;
        }

        mAdnUpdatingWaiter = response;

        mCi.updateAdnRecord(updateAdn,
                obtainMessage(EVENT_UPDATE_ADN_RECORD_DONE, index, 0, newAdn));
    }

    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)
                && mPhoneId == phoneId) {
                    log("ACTION_SIM_STATE_CHANGED intent received simStatus: " + simStatus + "phoneId: " + phoneId);
                    invalidateAdnCache();
                }
            }
        }
    };

    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;
        int efid;

        switch(msg.what) {
            case EVENT_INIT_ADN_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    invalidateAdnCache();

                    Intent intent = new Intent(ACTION_ADN_INIT_DONE);
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                    SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhoneId);
                    log("broadcast intent ACTION_ADN_INIT_DONE for mPhoneId=" + mPhoneId);
                    mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                } else {
                    log("Init ADN done Exception: " + ar.exception);
                }

                break;

            case EVENT_QUERY_ADN_RECORD_DONE:
                log("Querying ADN record done");
                if (ar.exception != null) {
                    synchronized (mLock) {
                        mLock.notify();
                    }

                    for (Message response : mAdnLoadingWaiters) {
                        sendErrorResponse(response, "Query adn record failed" + ar.exception);
                    }
                    mAdnLoadingWaiters.clear();
                    break;
                }
                mAdnCount = ((int[]) (ar.result))[0];
                mValidAdnCount = ((int[]) (ar.result))[1];
                mEmailCount = ((int[]) (ar.result))[2];
                mValidEmailCount = ((int[]) (ar.result))[3];
                mAddNumCount = ((int[]) (ar.result))[4];
                mValidAddNumCount = ((int[]) (ar.result))[5];
                log("Max ADN count is: " + mAdnCount
                    + ", Valid ADN count is: " + mValidAdnCount
                    + ", Email count is: " + mEmailCount
                    + ", Valid Email count is: " + mValidEmailCount
                    + ", Add number count is: " + mAddNumCount
                    + ", Valid Add number count is: " + mValidAddNumCount);

                if(mValidAdnCount == 0 || mRecCount == mValidAdnCount) {
                    sendMessage(obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE));
                }
                break;

            case EVENT_LOAD_ADN_RECORD_DONE:
                log("Loading ADN record done");
                if (ar.exception != null) {
                    break;
                }

                SimPhoneBookAdnRecord[] AdnRecordsGroup = (SimPhoneBookAdnRecord[])(ar.result);
                for (int i = 0 ; i < AdnRecordsGroup.length ; i++) {
                    if (AdnRecordsGroup[i] != null) {
                        mSimPbRecords.add(new AdnRecord(0,
                                                AdnRecordsGroup[i].getRecordIndex(),
                                                AdnRecordsGroup[i].getAlphaTag(),
                                                AdnRecordsGroup[i].getNumber(),
                                                AdnRecordsGroup[i].getEmails(),
                                                AdnRecordsGroup[i].getAdNumbers()));
                        mRecCount ++;
                    }
                }

                if(mRecCount == mValidAdnCount) {
                    sendMessage(obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE));
                }
                break;

            case EVENT_LOAD_ALL_ADN_LIKE_DONE:
                log("Loading all ADN records done");
                synchronized (mLock) {
                    mLock.notify();
                }

                notifyAndClearWaiters();
                break;

            case EVENT_UPDATE_ADN_RECORD_DONE:
                log("Update ADN record done");
                Exception e = null;

                if (ar.exception == null) {
                    int index = msg.arg1;
                    AdnRecord adn = (AdnRecord) (ar.userObj);
                    int recordIndex = ((int[]) (ar.result))[0];

                    if(index == 0) {
                        //add contact
                        log("Record number for added ADN is " + recordIndex);
                        adn.setRecordNumber(recordIndex);
                        mSimPbRecords.add(adn);
                        mValidAdnCount ++;
                    } else if (adn.isEmpty()){
                        //delete contact
                        int adnRecordIndex = mSimPbRecords.get(index - 1).getRecordNumber();
                        log("Record number for deleted ADN is " + adnRecordIndex);
                        if(recordIndex == adnRecordIndex) {
                            mSimPbRecords.remove(index - 1);
                            mValidAdnCount --;
                        } else {
                            e = new RuntimeException(
                                "The index for deleted ADN record did not match");
                        }
                    } else {
                        //Change contact
                        int adnRecordIndex = mSimPbRecords.get(index - 1).getRecordNumber();
                        log("Record number for changed ADN is " + adnRecordIndex);
                        if(recordIndex == adnRecordIndex) {
                            adn.setRecordNumber(recordIndex);
                            mSimPbRecords.set(index - 1, adn);
                        } else {
                            e = new RuntimeException(
                                "The index for changed ADN record did not match");
                        }
                    }
                } else {
                   e = new RuntimeException("Update adn record failed",
                                ar.exception);
                }

                if (mAdnUpdatingWaiter != null) {
                    AsyncResult.forMessage(mAdnUpdatingWaiter, null, e);
                    mAdnUpdatingWaiter.sendToTarget();
                    mAdnUpdatingWaiter = null;
                }
                break;

            case EVENT_SIM_REFRESH:
                ar = (AsyncResult)msg.obj;
                log("SIM REFRESH occurred");
                if (ar.exception == null) {
                    IccRefreshResponse refreshRsp = (IccRefreshResponse)ar.result;
                    if (refreshRsp == null) {
                        if (DBG) log("IccRefreshResponse received is null");
                        break;
                    }

                    if(refreshRsp.refreshResult == IccRefreshResponse.REFRESH_RESULT_FILE_UPDATE ||
                        refreshRsp.refreshResult == IccRefreshResponse.REFRESH_RESULT_INIT) {
                          invalidateAdnCache();
                    }
                } else {
                    log("SIM refresh Exception: " + ar.exception);
                }
                break;
        }

    }

    public int getAdnCount() {
        return mAdnCount;
    }

    public int getUsedAdnCount() {
        return mValidAdnCount;
    }

    public int getEmailCount() {
        return mEmailCount;
    }

    public int getUsedEmailCount() {
        return mValidEmailCount;
    }

    public int getAnrCount() {
        return mAddNumCount;
    }

    public int getUsedAnrCount() {
        return mValidAddNumCount;
    }

    private void log(String msg) {
        if(DBG) Rlog.d(LOG_TAG, msg);
    }

    public void invalidateAdnCache() {
        log("invalidateAdnCache");
        mRefreshAdnCache = true;
    }

    private void refreshAdnCache() {
        log("refreshAdnCache");
        mSimPbRecords.clear();
        queryAdnRecord();
    }
}
