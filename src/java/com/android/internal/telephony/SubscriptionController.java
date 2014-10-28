/*
* Copyright (C) 2014 The Android Open Source Project
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

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.net.NetworkRequest;
import android.preference.PreferenceManager;

import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.dataconnection.DdsScheduler;
import com.android.internal.telephony.dataconnection.DdsSchedulerAc;

import android.provider.BaseColumns;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.NumberFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * SubscriptionController to provide an inter-process communication to
 * access Sms in Icc.
 *
 * Any setters which take subId, slotId or phoneId as a parameter will throw an exception if the
 * parameter equals the corresponding INVALID_XXX_ID or DEFAULT_XXX_ID.
 *
 * All getters will lookup the corresponding default if the parameter is DEFAULT_XXX_ID. Ie calling
 * getPhoneId(DEFAULT_SUB_ID) will return the same as getPhoneId(getDefaultSubId()).
 *
 * Finally, any getters which perform the mapping between subscriptions, slots and phones will
 * return the corresponding INVALID_XXX_ID if the parameter is INVALID_XXX_ID. All other getters
 * will fail and return the appropriate error value. Ie calling getSlotId(INVALID_SUB_ID) will
 * return INVALID_SLOT_ID and calling getSubInfoUsingSubId(INVALID_SUB_ID) will return null.
 *
 */
public class SubscriptionController extends ISub.Stub {
    static final String LOG_TAG = "SubController";
    static final boolean DBG = true;
    static final boolean VDBG = false;

    protected final Object mLock = new Object();
    protected boolean mSuccess;

    /** The singleton instance. */
    private static SubscriptionController sInstance = null;
    protected static PhoneProxy[] sProxyPhones;
    protected static Context mContext;
    protected static CallManager mCM;

    //FIXME this does not allow for multiple subs in a slot
    private static HashMap<Integer, Integer> mSimInfo = new HashMap<Integer, Integer>();
    private static int mDefaultVoiceSubId = SubscriptionManager.DEFAULT_SUB_ID;
    private static int mDefaultPhoneId = 0;

    private static final int EVENT_WRITE_MSISDN_DONE = 1;

    private int[] colorArr;

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_WRITE_MSISDN_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        mSuccess = (ar.exception == null);
                        logd("EVENT_WRITE_MSISDN_DONE, mSuccess = "+mSuccess);
                        mLock.notifyAll();
                    }
                    break;
            }
        }
    };

    private static final int EVENT_SET_DEFAULT_DATA_DONE = 1;
    private DataConnectionHandler mDataConnectionHandler;
    private  DctController mDctController;

    private HashMap<Long, OnDemandDdsLockNotifier> mOnDemandDdsLockNotificationRegistrants =
        new HashMap<Long, OnDemandDdsLockNotifier>();

    private DdsScheduler mScheduler;
    private DdsSchedulerAc mSchedulerAc;

    // Dummy subId is used when no SIMs present on device
    // with MSIM configuration and this is corresponds
    // to phoneId 0.
    private static final int DUMMY_SUB_ID = -1;

    public static SubscriptionController init(Phone phone) {
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionController(phone);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    public static SubscriptionController init(Context c, CommandsInterface[] ci) {
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionController(c);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    public static SubscriptionController getInstance() {
        if (sInstance == null)
        {
           Log.wtf(LOG_TAG, "getInstance null");
        }

        return sInstance;
    }

    private SubscriptionController(Context c) {
        logd("SubscriptionController init by Context");
        mContext = c;
        mCM = CallManager.getInstance();

        if(ServiceManager.getService("isub") == null) {
                ServiceManager.addService("isub", this);
        }

        logd("SubscriptionController init by Context");
        mDataConnectionHandler = new DataConnectionHandler();

        mScheduler = DdsScheduler.getInstance();

        mSchedulerAc = new DdsSchedulerAc();
        mSchedulerAc.connect(mContext, mDataConnectionHandler, mScheduler.getHandler());

    }

    public long getSubIdFromNetworkRequest(NetworkRequest n) {
        long subId;
        if (n == null) {
            return getDefaultDataSubId();
        }

        String str = n.networkCapabilities.getNetworkSpecifier();
        try {
            subId = Long.parseLong(str);
        } catch(NumberFormatException e) {
            loge("Exception e = " + e);
            subId = getDefaultDataSubId();
        }

        return subId;
    }

    public void startOnDemandDataSubscriptionRequest(NetworkRequest n) {
        logd("startOnDemandDataSubscriptionRequest = " + n);
        mSchedulerAc.allocateDds(n);
    }

    public void stopOnDemandDataSubscriptionRequest(NetworkRequest n) {
        logd("stopOnDemandDataSubscriptionRequest = " + n);
        mSchedulerAc.freeDds(n);
    }

    private boolean isSubInfoReady() {
        return mSimInfo.size() > 0;
    }

    private SubscriptionController(Phone phone) {
        mContext = phone.getContext();
        mCM = CallManager.getInstance();

        if(ServiceManager.getService("isub") == null) {
                ServiceManager.addService("isub", this);
        }

        logdl("[SubscriptionController] init by Phone");
    }

    /**
     * Make sure the caller has the READ_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceSubscriptionPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE,
                "Requires READ_PHONE_STATE");
    }

    /**
     * Make sure the caller has the READ_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceSubscriptionPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE,
                "Requires READ_PHONE_STATE");
    }

    /**
     * Broadcast when subinfo settings has chanded
     * @SubId The unique SubInfoRecord index in database
     * @param columnName The column that is updated
     * @param intContent The updated integer value
     * @param stringContent The updated string value
     */
     private void broadcastSimInfoContentChanged(int subId,
            String columnName, int intContent, String stringContent) {

        Intent intent = new Intent(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        intent.putExtra(BaseColumns._ID, subId);
        intent.putExtra(TelephonyIntents.EXTRA_COLUMN_NAME, columnName);
        intent.putExtra(TelephonyIntents.EXTRA_INT_CONTENT, intContent);
        intent.putExtra(TelephonyIntents.EXTRA_STRING_CONTENT, stringContent);
        if (intContent != SubscriptionManager.DEFAULT_INT_VALUE) {
            logd("SubInfoRecord" + subId + " changed, " + columnName + " -> " +  intContent);
        } else {
            logd("[broadcastSimInfoContentChanged] subId" + subId
                    + " changed, " + columnName + " -> " +  stringContent);
        }
        mContext.sendBroadcast(intent);
    }


    /**
     * New SubInfoRecord instance and fill in detail info
     * @param cursor
     * @return the query result of desired SubInfoRecord
     */
    private SubInfoRecord getSubInfoRecord(Cursor cursor) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
            String iccId = cursor.getString(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.ICC_ID));
            int simSlotIndex = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.SIM_ID));
            String displayName = cursor.getString(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.DISPLAY_NAME));
            String carrierName = cursor.getString(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.CARRIER_NAME));
            int nameSource = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.NAME_SOURCE));
            int iconTint = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.COLOR));
            String number = cursor.getString(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.NUMBER));
            int dataRoaming = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.DATA_ROAMING));
            // Get the blank bitmap for this SubInfoRecord
            Bitmap iconBitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    com.android.internal.R.drawable.sim_dark_blue);
            int mcc = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.MCC));
            int mnc = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.MNC));
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.SUB_STATE));
            int nwMode = cursor.getInt(cursor.getColumnIndexOrThrow(
                    SubscriptionManager.NETWORK_MODE));

            logd("[getSubInfoRecord] id:" + id + " iccid:" + iccId + " simSlotIndex:" + simSlotIndex
                    + " displayName:" + displayName + " nameSource:" + nameSource
                    + " iconTint:" + iconTint + " number:" + number + " dataRoaming:" + dataRoaming
                    + " mcc:" + mcc + " mnc:" + mnc + " status:" + status + " nwMode:" + nwMode);

            return new SubInfoRecord(id, iccId, simSlotIndex, displayName, carrierName, nameSource,
                    iconTint, number, dataRoaming, iconBitmap, mcc, mnc, status, nwMode);
    }

    /**
     * Query SubInfoRecord(s) from subinfo database
     * @param context Context provided by caller
     * @param selection A filter declaring which rows to return
     * @param queryKey query key content
     * @return Array list of queried result from database
     */
     private List<SubInfoRecord> getSubInfo(String selection, Object queryKey) {
        logd("selection:" + selection + " " + queryKey);
        String[] selectionArgs = null;
        if (queryKey != null) {
            selectionArgs = new String[] {queryKey.toString()};
        }
        ArrayList<SubInfoRecord> subList = null;
        Cursor cursor = mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI,
                null, selection, selectionArgs, null);
        try {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    SubInfoRecord subInfo = getSubInfoRecord(cursor);
                    if (subInfo != null)
                    {
                        if (subList == null)
                        {
                            subList = new ArrayList<SubInfoRecord>();
                        }
                        subList.add(subInfo);
                }
                }
            } else {
                logd("Query fail");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return subList;
    }

    /**
     * Find unused color to be set for new SubInfoRecord
     * @return RGB integer value of color
     */
    private int getUnusedColor() {
        List<SubInfoRecord> availableSubInfos = SubscriptionManager.getActiveSubInfoList();
        colorArr = mContext.getResources().getIntArray(com.android.internal.R.array.sim_colors);
        for (int i = 0; i < colorArr.length; i++) {
            int j;
            for (j = 0; j < availableSubInfos.size(); j++) {
                if (colorArr[i] == availableSubInfos.get(j).getIconTint()) {
                    break;
                }
            }
            if (j == availableSubInfos.size()) {
                return colorArr[i];
            }
        }
        return colorArr[availableSubInfos.size() % colorArr.length];
    }

    /**
     * Get the SubInfoRecord according to an index
     * @param context Context provided by caller
     * @param subId The unique SubInfoRecord index in database
     * @return SubInfoRecord, maybe null
     */
    @Override
    public SubInfoRecord getSubInfoForSubscriber(int subId) {
        logd("[getSubInfoForSubscriberx]+ subId:" + subId);
        enforceSubscriptionPermission();

        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            subId = getDefaultSubId();
        }
        if (!SubscriptionManager.isValidSubId(subId) || !isSubInfoReady()) {
            logd("[getSubInfoUsingSubIdx]- invalid subId or not ready = " + subId);
            return null;
        }
        Cursor cursor = mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI,
                null, BaseColumns._ID + "=?", new String[] {Long.toString(subId)}, null);
        try {
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    logd("[getSubInfoForSubscriberx]- Info detail:");
                    return getSubInfoRecord(cursor);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        logd("[getSubInfoForSubscriber]- null info return");

        return null;
    }

    /**
     * Get the SubInfoRecord according to an IccId
     * @param context Context provided by caller
     * @param iccId the IccId of SIM card
     * @return SubInfoRecord, maybe null
     */
    @Override
    public List<SubInfoRecord> getSubInfoUsingIccId(String iccId) {
        logd("[getSubInfoUsingIccId]+ iccId:" + iccId);
        enforceSubscriptionPermission();

        if (iccId == null || !isSubInfoReady()) {
            logd("[getSubInfoUsingIccId]- null iccid or not ready");
            return null;
        }
        Cursor cursor = mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI,
                null, SubscriptionManager.ICC_ID + "=?", new String[] {iccId}, null);
        ArrayList<SubInfoRecord> subList = null;
        try {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    SubInfoRecord subInfo = getSubInfoRecord(cursor);
                    if (subInfo != null)
                    {
                        if (subList == null)
                        {
                            subList = new ArrayList<SubInfoRecord>();
                        }
                        subList.add(subInfo);
                }
                }
            } else {
                logd("Query fail");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return subList;
    }

    /**
     * Get the SubInfoRecord according to slotId
     * @param context Context provided by caller
     * @param slotId the slot which the SIM is inserted
     * @return SubInfoRecord, maybe null
     */
    @Override
    public List<SubInfoRecord> getSubInfoUsingSlotId(int slotId) {
        return getSubInfoUsingSlotIdWithCheck(slotId, true);
    }

    /**
     * Get all the SubInfoRecord(s) in subinfo database
     * @param context Context provided by caller
     * @return Array list of all SubInfoRecords in database, include thsoe that were inserted before
     */
    @Override
    public List<SubInfoRecord> getAllSubInfoList() {
        logd("[getAllSubInfoList]+");
        enforceSubscriptionPermission();

        List<SubInfoRecord> subList = null;
        subList = getSubInfo(null, null);
        if (subList != null) {
            logd("[getAllSubInfoList]- " + subList.size() + " infos return");
        } else {
            logd("[getAllSubInfoList]- no info return");
        }

        return subList;
    }

    /**
     * Get the SubInfoRecord(s) of the currently inserted SIM(s)
     * @param context Context provided by caller
     * @return Array list of currently inserted SubInfoRecord(s)
     */
    @Override
    public List<SubInfoRecord> getActivatedSubInfoList() {
        logd("[getActivatedSubInfoList]+");
        enforceSubscriptionPermission();

        List<SubInfoRecord> subList = null;

        if (!isSubInfoReady()) {
            logdl("[getActiveSubInfoList] Sub Controller not ready");
            return subList;
        }

        subList = getSubInfo(SubscriptionManager.SIM_ID
                + "!=" + SubscriptionManager.INVALID_SLOT_ID, null);
        if (subList != null) {
            logdl("[getActiveSubInfoList]- " + subList.size() + " infos return");
        } else {
            logdl("[getActiveSubInfoList]- no info return");
        }

        return subList;
    }

    /**
     * Get the SUB count of active SUB(s)
     * @return active SIM count
     */
    @Override
    public int getActivatedSubInfoCount() {
        logd("[getActivatedSubInfoCount]+");
        List<SubInfoRecord> records = getActivatedSubInfoList();
        if (records == null) {
            logd("[getActivatedSubInfoCount] records null");
            return 0;
        }
        logd("[getActivatedSubInfoCount]- count: " + records.size());
        return records.size();
    }

    /**
     * Get the SUB count of all SUB(s) in subinfo database
     * @param context Context provided by caller
     * @return all SIM count in database, include what was inserted before
     */
    @Override
    public int getAllSubInfoCount() {
        logd("[getAllSubInfoCount]+");
        enforceSubscriptionPermission();

        Cursor cursor = mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI,
                null, null, null, null);
        try {
            if (cursor != null) {
                int count = cursor.getCount();
                logd("[getAllSubInfoCount]- " + count + " SUB(s) in DB");
                return count;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        logd("[getAllSubInfoCount]- no SUB in DB");

        return 0;
    }

    /**
     * Add a new SubInfoRecord to subinfo database if needed
     * @param context Context provided by caller
     * @param iccId the IccId of the SIM card
     * @param slotId the slot which the SIM is inserted
     * @return the URL of the newly created row or the updated row
     */
    @Override
    public int addSubInfoRecord(String iccId, int slotId) {
        logd("[addSubInfoRecord]+ iccId:" + iccId + " slotId:" + slotId);
        enforceSubscriptionPermission();

        if (iccId == null) {
            logdl("[addSubInfoRecord]- null iccId");
        }

        int[] subIds = getSubId(slotId);
        if (subIds == null || subIds.length == 0) {
            logdl("[addSubInfoRecord]- getSubId fail");
            return 0;
        }

        String nameToSet;
        String CarrierName = TelephonyManager.getDefault().getSimOperator(subIds[0]);
        logdl("[addSubInfoRecord] CarrierName = " + CarrierName);
        String simCarrierName =
                TelephonyManager.getDefault().getSimOperatorName(subIds[0]);

        if (!TextUtils.isEmpty(simCarrierName)) {
            nameToSet = simCarrierName;
        } else {
            nameToSet = "CARD " + Integer.toString(slotId + 1);
        }
        logdl("[addSubInfoRecord] sim name = " + nameToSet);
        logdl("[addSubInfoRecord] carrier name = " + simCarrierName);

        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(SubscriptionManager.CONTENT_URI,
                new String[] {BaseColumns._ID, SubscriptionManager.SIM_ID,
                SubscriptionManager.NAME_SOURCE}, SubscriptionManager.ICC_ID + "=?",
                new String[] {iccId}, null);

        int color = getUnusedColor();

        try {
            if (cursor == null || !cursor.moveToFirst()) {
                ContentValues value = new ContentValues();
                value.put(SubscriptionManager.ICC_ID, iccId);
                // default SIM color differs between slots
                value.put(SubscriptionManager.COLOR, color);
                value.put(SubscriptionManager.SIM_ID, slotId);
                value.put(SubscriptionManager.DISPLAY_NAME, nameToSet);
                value.put(SubscriptionManager.CARRIER_NAME,
                        !TextUtils.isEmpty(simCarrierName) ? simCarrierName :
                        mContext.getString(com.android.internal.R.string.unknownName));
                Uri uri = resolver.insert(SubscriptionManager.CONTENT_URI, value);
                logd("[addSubInfoRecord]- New record created: " + uri);
            } else {
                int subId = cursor.getInt(0);
                int oldSimInfoId = cursor.getInt(1);
                int nameSource = cursor.getInt(2);
                ContentValues value = new ContentValues();

                if (slotId != oldSimInfoId) {
                    value.put(SubscriptionManager.SIM_ID, slotId);
                }

                if (nameSource != SubscriptionManager.USER_INPUT) {
                    value.put(SubscriptionManager.DISPLAY_NAME, nameToSet);
                }

                if (!TextUtils.isEmpty(simCarrierName)) {
                    value.put(SubscriptionManager.CARRIER_NAME, simCarrierName);
                }

                if (value.size() > 0) {
                    resolver.update(SubscriptionManager.CONTENT_URI, value,
                            BaseColumns._ID + "=" + Long.toString(subId), null);
                }

                logd("[addSubInfoRecord]- Record already exist");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        cursor = resolver.query(SubscriptionManager.CONTENT_URI, null,
                SubscriptionManager.SIM_ID + "=?", new String[] {String.valueOf(slotId)}, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int subId = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
                    // If mSimInfo already has a valid subId for a slotId/phoneId,
                    // do not add another subId for same slotId/phoneId.
                    Integer currentSubId = mSimInfo.get(slotId);
                    if (currentSubId == null || !SubscriptionManager.isValidSubId(currentSubId)) {
                        // TODO While two subs active, if user deactivats first

                        // one, need to update the default subId with second
                        // one.
                        mSimInfo.put(slotId, subId);
                        int simCount = TelephonyManager.getDefault().getSimCount();
                        int defaultSubId = getDefaultSubId();
                        logdl("[addSubInfoRecord] mSimInfo.size=" + mSimInfo.size()
                                + " slotId=" + slotId + " subId=" + subId
                                + " defaultSubId=" + defaultSubId + " simCount=" + simCount);

                        // Set the default sub if not set
                        if (!SubscriptionManager.isValidSubId(defaultSubId) || simCount == 1) {
                            setDefaultSubId(subId);
                        }
                        // If single sim device, set this subscription as the default for everything
                        if (simCount == 1) {
                            logd("[addSubInfoRecord] one sim set defaults to subId=" + subId);
                            setDefaultDataSubId(subId);
                            setDataSubId(subId);
                            setDefaultSmsSubId(subId);
                            setDefaultVoiceSubId(subId);
                        }
                    } else {
                        logd("[addSubInfoRecord] currentSubId != null && currentSubId is valid, IGNORE");
                    }
                    logd("[addSubInfoRecord]- hashmap("+slotId+","+subId+")");
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        int size = mSimInfo.size();
        logd("[addSubInfoRecord]- info size="+size);

        // Once the records are loaded, notify DcTracker
        updateAllDataConnectionTrackers();

        // FIXME this does not match the javadoc
        return 1;
    }

    /**
     * Set SIM color tint by simInfo index
     * @param tint the tint color of the SIM
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    @Override
    public int setIconTint(int tint, int subId) {
        logd("[setIconTint]+ tint:" + tint + " subId:" + subId);
        enforceSubscriptionPermission();

        validateSubId(subId);
        ContentValues value = new ContentValues(1);
        value.put(SubscriptionManager.COLOR, tint);
        logd("[setIconTint]- tint:" + tint + " set");

        int result = mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value,
                BaseColumns._ID + "=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, SubscriptionManager.COLOR,
                tint, SubscriptionManager.DEFAULT_STRING_VALUE);

        return result;
    }

    /**
     * Set display name by simInfo index
     * @param context Context provided by caller
     * @param displayName the display name of SIM card
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    @Override
    public int setDisplayName(String displayName, int subId) {
        return setDisplayNameUsingSrc(displayName, subId, -1);
    }

    /**
     * Set display name by simInfo index with name source
     * @param context Context provided by caller
     * @param displayName the display name of SIM card
     * @param subId the unique SubInfoRecord index in database
     * @param nameSource, 0: DEFAULT_SOURCE, 1: SIM_SOURCE, 2: USER_INPUT
     * @return the number of records updated
     */
    @Override
    public int setDisplayNameUsingSrc(String displayName, int subId, long nameSource) {
        logd("[setDisplayName]+  displayName:" + displayName + " subId:" + subId
                + " nameSource:" + nameSource);
        enforceSubscriptionPermission();

        validateSubId(subId);
        String nameToSet;
        if (displayName == null) {
            nameToSet = mContext.getString(SubscriptionManager.DEFAULT_NAME_RES);
        } else {
            nameToSet = displayName;
        }
        ContentValues value = new ContentValues(1);
        value.put(SubscriptionManager.DISPLAY_NAME, nameToSet);
        if (nameSource >= SubscriptionManager.DEFAULT_SOURCE) {
            logd("Set nameSource=" + nameSource);
            value.put(SubscriptionManager.NAME_SOURCE, nameSource);
        }
        logd("[setDisplayName]- mDisplayName:" + nameToSet + " set");

        int result = mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value,
                BaseColumns._ID + "=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, SubscriptionManager.DISPLAY_NAME,
                SubscriptionManager.DEFAULT_INT_VALUE, nameToSet);

        return result;
    }

    /**
     * Set phone number by subId
     * @param context Context provided by caller
     * @param number the phone number of the SIM
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    @Override
    public int setDisplayNumber(String number, int subId) {
        logd("[setDisplayNumber]+ number:" + number + " subId:" + subId);
        enforceSubscriptionPermission();

        validateSubId(subId);
        int result = 0;
        int phoneId = getPhoneId(subId);

        if (number == null || phoneId < 0 ||
                phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            logd("[setDispalyNumber]- fail");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put(SubscriptionManager.NUMBER, number);
        logd("[setDisplayNumber]- number:" + number + " set");

        Phone phone = sProxyPhones[phoneId];
        String alphaTag = TelephonyManager.getDefault().getLine1AlphaTag(subId);

        synchronized(mLock) {
            mSuccess = false;
            Message response = mHandler.obtainMessage(EVENT_WRITE_MSISDN_DONE);

            phone.setLine1Number(alphaTag, number, response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                loge("interrupted while trying to write MSISDN");
            }
        }

        if (mSuccess) {
            result = mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value,
                    BaseColumns._ID + "=" + Long.toString(subId), null);
            logd("[setDisplayNumber]- update result :" + result);
            broadcastSimInfoContentChanged(subId, SubscriptionManager.NUMBER,
                    SubscriptionManager.DEFAULT_INT_VALUE, number);
        }

        return result;
    }

    /**
     * Set data roaming by simInfo index
     * @param context Context provided by caller
     * @param roaming 0:Don't allow data when roaming, 1:Allow data when roaming
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    @Override
    public int setDataRoaming(int roaming, int subId) {
        logd("[setDataRoaming]+ roaming:" + roaming + " subId:" + subId);
        enforceSubscriptionPermission();

        validateSubId(subId);
        if (roaming < 0) {
            logd("[setDataRoaming]- fail");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put(SubscriptionManager.DATA_ROAMING, roaming);
        logd("[setDataRoaming]- roaming:" + roaming + " set");

        int result = mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value,
                BaseColumns._ID + "=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, SubscriptionManager.DATA_ROAMING,
                roaming, SubscriptionManager.DEFAULT_STRING_VALUE);

        return result;
    }

    /**
     * Set MCC/MNC by subscription ID
     * @param mccMnc MCC/MNC associated with the subscription
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    public int setMccMnc(String mccMnc, int subId) {
        int mcc = 0;
        int mnc = 0;
        try {
            mcc = Integer.parseInt(mccMnc.substring(0,3));
            mnc = Integer.parseInt(mccMnc.substring(3));
        } catch (NumberFormatException e) {
            logd("[setMccMnc] - couldn't parse mcc/mnc: " + mccMnc);
        }
        logd("[setMccMnc]+ mcc/mnc:" + mcc + "/" + mnc + " subId:" + subId);
        ContentValues value = new ContentValues(2);
        value.put(SubscriptionManager.MCC, mcc);
        value.put(SubscriptionManager.MNC, mnc);

        int result = mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value,
                BaseColumns._ID + "=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, SubscriptionManager.MCC, mcc, null);

        return result;
    }

    @Override
    public int getSlotId(int subId) {
        if (VDBG) printStackTrace("[getSlotId] subId=" + subId);

        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            subId = getDefaultSubId();
        }
        if (!SubscriptionManager.isValidSubId(subId)) {
            logd("[getSlotId]- subId invalid");
            return SubscriptionManager.INVALID_SLOT_ID;
        }

        int size = mSimInfo.size();

        if (size == 0)
        {
            logd("[getSlotId]- size == 0, return SIM_NOT_INSERTED instead");
            return SubscriptionManager.SIM_NOT_INSERTED;
        }

        for (Entry<Integer, Integer> entry: mSimInfo.entrySet()) {
            int sim = entry.getKey();
            int sub = entry.getValue();

            if (subId == sub)
            {
                if (VDBG) logv("[getSlotId]- return = " + sim);
                return sim;
            }
        }

        logd("[getSlotId]- return fail");
        return SubscriptionManager.INVALID_SLOT_ID;
    }

    /**
     * Return the subId for specified slot Id.
     * @deprecated
     */
    @Deprecated
    public int[] getSubId(int slotId) {
        if (VDBG) printStackTrace("[getSubId] slotId=" + slotId);

        if (slotId == SubscriptionManager.DEFAULT_SLOT_ID) {
            logd("[getSubId]- default slotId");
            slotId = getSlotId(getDefaultSubId());
        }

        //FIXME remove this
        final int[] DUMMY_VALUES = {-1 - slotId, -1 - slotId};

        if (!SubscriptionManager.isValidSlotId(slotId)) {
            logd("[getSubId]- invalid slotId");
            return null;
        }

        //FIXME remove this
        if (slotId < 0) {
            logd("[getSubId]- slotId < 0, return dummy instead");
            return DUMMY_VALUES;
        }

        int size = mSimInfo.size();

        if (size == 0) {
            logd("[getSubId]- size == 0, return dummy instead");
            //FIXME return null
            return DUMMY_VALUES;
        }

        ArrayList<Integer> subIds = new ArrayList<Integer>();
        for (Entry<Integer, Integer> entry: mSimInfo.entrySet()) {
            int slot = entry.getKey();
            int sub = entry.getValue();
            if (slotId == slot) {
                subIds.add(sub);
            }
        }

        logd("[getSubId]-, subIds = " + subIds);
        int numSubIds = subIds.size();

        if (numSubIds == 0) {
            logd("[getSubId]- numSubIds == 0, return dummy instead");
            return DUMMY_VALUES;
        }

        int[] subIdArr = new int[numSubIds];
        for (int i = 0; i < numSubIds; i++) {
            subIdArr[i] = subIds.get(i);
        }

        return subIdArr;
    }

    @Override
    public int getPhoneId(int subId) {
        if (VDBG) printStackTrace("[getPhoneId] subId=" + subId);
        int phoneId;

        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            logd("[getPhoneId]- default subId");
            subId = getDefaultSubId();
        }

        if (!SubscriptionManager.isValidSubId(subId)) {
            logd("[getPhoneId]- invalid subId");
            return SubscriptionManager.INVALID_PHONE_ID;
        }

        //FIXME remove this
        if (subId == -1) {
            logd("[getPhoneId]- subId == -1, return dummy data");
            return 0;
        } else if (subId == -2) {
            logd("[getPhoneId]- subId == -2, return dummy data");
            return 1;
        }

        int size = mSimInfo.size();

        if (size == 0) {
            logd("getPhoneId, returning defaultPhoneId ");
            return mDefaultPhoneId;
        }

        // FIXME: Assumes phoneId == slotId
        for (Entry<Integer, Integer> entry: mSimInfo.entrySet()) {
            int sim = entry.getKey();
            int sub = entry.getValue();

            if (subId == sub) {
                logd("[getPhoneId]- return ="+sim);
                return sim;
            }
        }

        logd("[getPhoneId]- return fail");
        return mDefaultPhoneId;

    }

    /**
     * @return the number of records cleared
     */
    public int clearSubInfo() {
        enforceSubscriptionPermission();
        logd("[clearSubInfo]+");

        int size = mSimInfo.size();
        logd("[clearSubInfo]- info size="+size);

        if (size == 0) {
            return 0;
        }

        mSimInfo.clear();
        logd("[clearSubInfo]-");
        return size;
    }

    private void logvl(String msg) {
        logv(msg);
        mLocalLog.log(msg);
    }

    private void logv(String msg) {
        Rlog.v(LOG_TAG, msg);
    }

    private void logdl(String msg) {
        logd(msg);
        mLocalLog.log(msg);
    }

    private static void slogd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void logel(String msg) {
        loge(msg);
        mLocalLog.log(msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    @Override
    public int getDefaultSubId() {
        //FIXME: Make this smarter, need to handle data only and voice devices
        int subId = mDefaultVoiceSubId;
        if (VDBG) logv("[getDefaultSubId] value = " + subId);
        return subId;
    }

    @Override
    public void setDefaultSmsSubId(int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            throw new RuntimeException("setDefaultSmsSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultSmsSubId] subId=" + subId);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION, subId);
        broadcastDefaultSmsSubIdChanged(subId);
    }

    private void broadcastDefaultSmsSubIdChanged(int subId) {
        // Broadcast an Intent for default sms sub change
        Intent intent = new Intent(TelephonyIntents.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Override
    public int getDefaultSmsSubId() {
        int subId = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUB_ID);
        if (VDBG) logd("getDefaultSmsSubId, value = " + subId);
        return subId;
    }

    @Override
    public void setDefaultVoiceSubId(int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            throw new RuntimeException("setDefaultVoiceSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultVoiceSubId] subId=" + subId);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION, subId);
        broadcastDefaultVoiceSubIdChanged(subId);
    }

    private void broadcastDefaultVoiceSubIdChanged(int subId) {
        // Broadcast an Intent for default voice sub change
        logdl("[broadcastDefaultVoiceSubIdChanged] subId=" + subId);
        Intent intent = new Intent(TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Override
    public int getDefaultVoiceSubId() {
        int subId = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUB_ID);
        if (VDBG) logd("getDefaultVoiceSubId, value = " + subId);
        return subId;
    }

    /* Returns User SMS Prompt property,  enabled or not */
    @Override
    public boolean isSMSPromptEnabled() {
        boolean prompt = false;
        int value = 0;
        try {
            value = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.MULTI_SIM_SMS_PROMPT);
        } catch (SettingNotFoundException snfe) {
            loge("Settings Exception Reading Dual Sim SMS Prompt Values");
        }
        prompt = (value == 0) ? false : true ;
        if (VDBG) logd("SMS Prompt option:" + prompt);

       return prompt;
    }

    /*Sets User SMS Prompt property,  enable or not */
    @Override
    public void setSMSPromptEnabled(boolean enabled) {
        int value = (enabled == false) ? 0 : 1;
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_PROMPT, value);
        logd("setSMSPromptOption to " + enabled);
    }


    @Override
    public int getDefaultDataSubId() {
        int subId = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUB_ID);
        if (VDBG) logd("getDefaultDataSubId, value = " + subId);
        return subId;
    }

    @Override
    public int getCurrentDds() {
        return mScheduler.getCurrentDds();
    }


    private void updateDataSubId(AsyncResult ar) {
        Integer subId = (Integer)ar.result;
        int reqStatus = PhoneConstants.FAILURE;

        logd(" updateDataSubId,  subId=" + subId + " exception " + ar.exception);
        // Update newDds in database if the DDS request succeeded.
        if (ar.exception == null) {
            setDataSubId(subId);
            reqStatus = PhoneConstants.SUCCESS;
        }
        mScheduler.updateCurrentDds(null);
        broadcastDefaultDataSubIdChanged(reqStatus);
    }

    public void setDefaultDataSubId(int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            throw new RuntimeException("setDefaultDataSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultDataSubId] subId=" + subId);
        if (mDctController == null) {
            mDctController = DctController.getInstance();
            mDctController.registerForDefaultDataSwitchInfo(mDataConnectionHandler,
                    EVENT_SET_DEFAULT_DATA_DONE, null);
        }
        mDctController.setDefaultDataSubId(subId);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION, subId);
        broadcastDefaultDataSubIdChanged(subId);

        // FIXME is this still needed?
        updateAllDataConnectionTrackers();
    }

    public void setDataSubId(int subId) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION, subId);
    }

    private void updateAllDataConnectionTrackers() {
        // Tell Phone Proxies to update data connection tracker
        for (int phoneId = 0; phoneId < sProxyPhones.length; phoneId++) {
            sProxyPhones[phoneId].updateDataConnectionTracker();
        }
    }

    private void broadcastDefaultDataSubIdChanged(int subId) {
        // Broadcast an Intent for default data sub change
        logdl("[broadcastDefaultDataSubIdChanged] subId = " + getDefaultDataSubId()
                 + " status " + status);
        Intent intent = new Intent(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, getDefaultDataSubId());
        intent.putExtra(TelephonyIntents.EXTRA_RESULT, status);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* Sets the default subscription. If only one sub is active that
     * sub is set as default subId. If two or more  sub's are active
     * the first sub is set as default subscription
     */
    // FIXME
    public void setDefaultSubId(int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            throw new RuntimeException("setDefaultSubId called with DEFAULT_SUB_ID");
        }
        logd("[setDefaultSubId] subId=" + subId);
        if (SubscriptionManager.isValidSubId(subId)) {
            int phoneId = getPhoneId(subId);
            if (phoneId >= 0 && (phoneId < TelephonyManager.getDefault().getPhoneCount()
                    || TelephonyManager.getDefault().getSimCount() == 1)) {
                mDefaultVoiceSubId = subId;
                // Update MCC MNC device configuration information
                String defaultMccMnc = TelephonyManager.getDefault().getSimOperator(phoneId);
                MccTable.updateMccMncConfiguration(mContext, defaultMccMnc, false);

                // Broadcast an Intent for default sub change
                Intent intent = new Intent(TelephonyIntents.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId, subId);
                if (VDBG) {
                    logdl("[setDefaultSubId] broadcast default subId changed phoneId=" + phoneId
                            + " subId=" + subId);
                }
                mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            } else {
                if (VDBG) {
                    logdl("[setDefaultSubId] not set invalid phoneId=" + phoneId + " subId=" + subId);
                }
            }
        }
    }

    private class DataConnectionHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SET_DEFAULT_DATA_DONE:{
                    AsyncResult ar = (AsyncResult) msg.obj;
                    logd("EVENT_SET_DEFAULT_DATA_DONE subId:" + (Long)ar.result);
                    updateDataSubId(ar);
                    break;
                }
            }
        }
    }

    public void clearDefaultsForInactiveSubIds() {
        final List<SubInfoRecord> records = getActivatedSubInfoList();
        logd("[clearDefaultsForInactiveSubIds] records: " + records);
        if (shouldDefaultBeCleared(records, getDefaultDataSubId())) {
            logd("[clearDefaultsForInactiveSubIds]: clearing default data sub id");
            setDefaultDataSubId(SubscriptionManager.INVALID_SUB_ID);
        }
        if (shouldDefaultBeCleared(records, getDefaultSmsSubId())) {
            logd("[clearDefaultsForInactiveSubIds]: clearing default sms sub id");
            setDefaultSmsSubId(SubscriptionManager.INVALID_SUB_ID);
        }
        if (shouldDefaultBeCleared(records, getDefaultVoiceSubId())) {
            logd("[clearDefaultsForInactiveSubIds]: clearing default voice sub id");
            setDefaultVoiceSubId(SubscriptionManager.INVALID_SUB_ID);
        }
    }

    private boolean shouldDefaultBeCleared(List<SubInfoRecord> records, int subId) {
        logdl("[shouldDefaultBeCleared: subId] " + subId);
        if (records == null) {
            return true;
        }
        if (subId == SubscriptionManager.ASK_USER_SUB_ID && records.size() > 1) {
            // Only allow ASK_USER_SUB_ID if there is more than 1 subscription.
            return false;
        }
        for (SubInfoRecord record : records) {
            int id = record.getSubscriptionId();
            logdl("[shouldDefaultBeCleared] Record.id: " + id);
            if (id == subId) {
                logdl("[shouldDefaultBeCleared] return false subId is active, subId=" + subId);
                return false;
            }
        }
        return true;
    }

    // FIXME: We need we should not be assuming phoneId == slotId as it will not be true
    // when there are multiple subscriptions per sim and probably for other reasons.
    public int getSubIdUsingPhoneId(int phoneId) {
        int[] subIds = getSubId(phoneId);
        if (subIds == null || subIds.length == 0) {
            return SubscriptionManager.INVALID_SUB_ID;
        }
        return subIds[0];
    }

    public int[] getSubIdUsingSlotId(int slotId) {
        return getSubId(slotId);
    }

    public List<SubInfoRecord> getSubInfoUsingSlotIdWithCheck(int slotId, boolean needCheck) {
        logd("[getSubInfoUsingSlotIdWithCheck]+ slotId:" + slotId);
        enforceSubscriptionPermission();

        if (slotId == SubscriptionManager.DEFAULT_SLOT_ID) {
            slotId = getSlotId(getDefaultSubId());
        }
        if (!SubscriptionManager.isValidSlotId(slotId)) {
            logd("[getSubInfoUsingSlotIdWithCheck]- invalid slotId");
            return null;
        }

        if (needCheck && !isSubInfoReady()) {
            logd("[getSubInfoUsingSlotIdWithCheck]- not ready");
            return null;
        }

        Cursor cursor = mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI,
                null, SubscriptionManager.SIM_ID + "=?", new String[] {String.valueOf(slotId)}, null);
        ArrayList<SubInfoRecord> subList = null;
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    SubInfoRecord subInfo = getSubInfoRecord(cursor);
                    if (subInfo != null) {
                        if (subList == null) {
                            subList = new ArrayList<SubInfoRecord>();
                        }
                        subList.add(subInfo);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        logd("[getSubInfoUsingSlotId]- null info return");

        return subList;
    }

    private void validateSubId(int subId) {
        logd("validateSubId subId: " + subId);
        if (!SubscriptionManager.isValidSubId(subId)) {
            throw new RuntimeException("Invalid sub id passed as parameter");
        } else if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            throw new RuntimeException("Default sub id passed as parameter");
        }
    }

    public void updatePhonesAvailability(PhoneProxy[] phones) {
        sProxyPhones = phones;
    }

    /**
     * @return the list of subId's that are active, is never null but the length maybe 0.
     */
    @Override
    public int[] getActiveSubIdList() {
        Set<Entry<Integer, Integer>> simInfoSet = mSimInfo.entrySet();
        logdl("[getActiveSubIdList] simInfoSet=" + simInfoSet);

        int[] subIdArr = new int[simInfoSet.size()];
        int i = 0;
        for (Entry<Integer, Integer> entry: simInfoSet) {
            int sub = entry.getValue();
            subIdArr[i] = sub;
            i++;
        }

        logdl("[getActiveSubIdList] X subIdArr.length=" + subIdArr.length);
        return subIdArr;
    }

    @Override
    public void activateSubId(long subId) {
        if (getSubState(subId) == SubscriptionManager.ACTIVE) {
            logd("activateSubId: subscription already active, subId = " + subId);
            return;
        }

        int slotId = getSlotId(subId);
        SubscriptionHelper.getInstance().setUiccSubscription(slotId, SubscriptionManager.ACTIVE);
    }

    @Override
    public void deactivateSubId(long subId) {
        if (getSubState(subId) == SubscriptionManager.INACTIVE) {
            logd("activateSubId: subscription already deactivated, subId = " + subId);
            return;
        }

        int slotId = getSlotId(subId);
        SubscriptionHelper.getInstance().setUiccSubscription(slotId, SubscriptionManager.INACTIVE);
    }

    public void setNwMode(long subId, int nwMode) {
        logd("setNwMode, nwMode: " + nwMode + " subId: " + subId);
        ContentValues value = new ContentValues(1);
        value.put(SubscriptionManager.NETWORK_MODE, nwMode);
        mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI,
                value, BaseColumns._ID + "=" + Long.toString(subId), null);
    }

    public int getNwMode(long subId) {
        SubInfoRecord subInfo = getSubInfoForSubscriber(subId);
        if (subInfo != null)  {
            return subInfo.mNwMode;
        } else {
            loge("getSubState: invalid subId = " + subId);
            return SubscriptionManager.DEFAULT_NW_MODE;
        }
    }

    @Override
    public int setSubState(long subId, int subStatus) {
        int result = 0;
        logd("setSubState, subStatus: " + subStatus + " subId: " + subId);
        if (ModemStackController.getInstance().isStackReady()) {
            ContentValues value = new ContentValues(1);
            value.put(SubscriptionManager.SUB_STATE, subStatus);
            result = mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI,
                    value, BaseColumns._ID + "=" + Long.toString(subId), null);

        }
        broadcastSimInfoContentChanged(subId,
                SubscriptionManager.SUB_STATE, subStatus, SubscriptionManager.DEFAULT_STRING_VALUE);
        return result;
    }

    @Override
    public int getSubState(long subId) {
        SubInfoRecord subInfo = getSubInfoForSubscriber(subId);
        int subStatus = SubscriptionManager.INACTIVE;

        // Consider the subStatus from subInfo record only if the
        //  record is associated with a valid slot Id.
        if ((subInfo != null) && (subInfo.slotId >= 0)) {
            subStatus = subInfo.mStatus;
        }
        return subStatus;
    }

    /* setDds flag is used to trigger DDS switch request during
      device powerUp and when flex map performed */
    public void updateUserPrefs(boolean setDds) {
        List<SubInfoRecord> subInfoList = getActiveSubInfoList();
        int mActCount = 0;
        SubInfoRecord mNextActivatedSub = null;

        if (subInfoList == null) {
            logd("updateUserPrefs: subscription are not avaiable dds = " + getDefaultDataSubId()
                     + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() +
                     " setDDs = " + setDds);
            // If no SIM cards present on device, set dummy subId
            // as data/sms/voice preferred subId.
            setDefaultSubId(DUMMY_SUB_ID);
            setDefaultVoiceSubId(DUMMY_SUB_ID);
            setDefaultSmsSubId(DUMMY_SUB_ID);
            setDataSubId(DUMMY_SUB_ID);
            return;
        }

        //Get num of activated Subs and next available activated sub info.
        for (SubInfoRecord subInfo : subInfoList) {
            if (getSubState(subInfo.subId) == SubscriptionManager.ACTIVE) {
                mActCount++;
                if (mNextActivatedSub == null) mNextActivatedSub = subInfo;
            }
        }

        logd("updateUserPrefs: active sub count = " + mActCount + " dds = " + getDefaultDataSubId()
                 + " voice = " + getDefaultVoiceSubId() + " sms = "
                 + getDefaultSmsSubId() + " setDDs = " + setDds);
        //if activated sub count is less than 2, disable prompt.
        if (mActCount < 2) {
            setSMSPromptEnabled(false);
            setVoicePromptEnabled(false);
        }

        //if there are no activated subs available, no need to update. EXIT.
        if (mNextActivatedSub == null) return;

        if (getSubState(getDefaultSubId()) == SubscriptionManager.INACTIVE) {
            setDefaultSubId(mNextActivatedSub.subId);
        }

        long ddsSubId = getDefaultDataSubId();
        int ddsSubState = getSubState(ddsSubId);
        //if current data sub is not active, fallback to next active sub.
        if (setDds || (ddsSubState == SubscriptionManager.INACTIVE)) {
            if (ddsSubState == SubscriptionManager.INACTIVE) ddsSubId = mNextActivatedSub.subId;
            setDefaultDataSubId(ddsSubId);
        }
        //if current voice sub is not active and prompt not enabled, fallback to next active sub.
        if (getSubState(getDefaultVoiceSubId()) == SubscriptionManager.INACTIVE &&
            !isVoicePromptEnabled()) {
            setDefaultVoiceSubId(mNextActivatedSub.subId);
        }
        //if current sms sub is not active and prompt not enabled, fallback to next active sub.
        if (getSubState(getDefaultSmsSubId()) == SubscriptionManager.INACTIVE &&
            !isSMSPromptEnabled()) {
            setDefaultSmsSubId(mNextActivatedSub.subId);
        }
        logd("updateUserPrefs: after currentDds = " + getDefaultDataSubId() + " voice = " +
                 getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() +
                 " newDds = " + ddsSubId);

    }

    /* Returns User Voice Prompt property,  enabled or not */
    @Override
    public boolean isVoicePromptEnabled() {
        boolean prompt = false;
        int value = 0;
        try {
            value = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.MULTI_SIM_VOICE_PROMPT);
        } catch (SettingNotFoundException snfe) {
            loge("Settings Exception Reading Dual Sim Voice Prompt Values");
        }
        prompt = (value == 0) ? false : true ;
        if (VDBG) logd("Voice Prompt option:" + prompt);

       return prompt;
    }

    /*Sets User SMS Prompt property,  enable or not */
    @Override
    public void setVoicePromptEnabled(boolean enabled) {
        int value = (enabled == false) ? 0 : 1;
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_PROMPT, value);
        logd("setVoicePromptOption to " + enabled);
    }

    @Override
    public long getOnDemandDataSubId() {
        return getCurrentDds();
    }

    public void registerForOnDemandDdsLockNotification(long clientSubId,
            OnDemandDdsLockNotifier callback) {
        logd("registerForOnDemandDdsLockNotification for client=" + clientSubId);
        mOnDemandDdsLockNotificationRegistrants.put(clientSubId, callback);

    }

    /* {@hide} */
    public void notifyOnDemandDataSubIdChanged(NetworkRequest n) {
        OnDemandDdsLockNotifier notifier = mOnDemandDdsLockNotificationRegistrants.get(
                getSubIdFromNetworkRequest(n));
        if (notifier != null) {
            notifier.notifyOnDemandDdsLockGranted(n);
        } else {
            logd("No registrants for OnDemandDdsLockGranted event");
        }
    }

    public interface OnDemandDdsLockNotifier {
        public void notifyOnDemandDdsLockGranted(NetworkRequest n);
    }
    public void removeStaleSubPreferences(String prefKey) {
        List<SubInfoRecord> subInfoList = getAllSubInfoList();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        for (SubInfoRecord subInfo : subInfoList) {
            if (subInfo.slotId == -1) {
                sp.edit().remove(prefKey+subInfo.subId).commit();
            }
        }
    }

    /**
     * @return the list of subId's that are activated, is never null but the length maybe 0.
     */
    public long[] getActivatedSubIdList() {
        Set<Entry<Integer, Long>> simInfoSet = mSimInfo.entrySet();
        logd("getActivatedSubIdList: simInfoSet=" + simInfoSet);

        long[] subIdArr = new long[simInfoSet.size()];
        int i = 0;
        for (Entry<Integer, Long> entry: simInfoSet) {
            long sub = entry.getValue();
            subIdArr[i] = sub;
            i++;
        }

        logd("getActivatedSubIdList: X subIdArr.length=" + subIdArr.length);
        return subIdArr;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SubscriptionController:");
        pw.println(" defaultDataSubId=" + getDefaultDataSubId());
        pw.println(" defaultVoiceSubId=" + getDefaultVoiceSubId());
        pw.println(" defaultSmsSubId=" + getDefaultSmsSubId());

        pw.println(" defaultDataPhoneId=" + SubscriptionManager.getDefaultDataPhoneId());
        pw.println(" defaultVoicePhoneId=" + SubscriptionManager.getDefaultVoicePhoneId());
        pw.println(" defaultSmsPhoneId=" + SubscriptionManager.getDefaultSmsPhoneId());
        pw.flush();

        for (Entry<Integer, Integer> entry : mSimInfo.entrySet()) {
            pw.println(" mSimInfo[" + entry.getKey() + "]: subId=" + entry.getValue());
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        for (SubInfoRecord entry : getActiveSubInfoList()) {
            pw.println(" ActiveSubInfoList:" + entry.toString());
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        for (SubInfoRecord entry : getAllSubInfoList()) {
            pw.println(" AllSubInfoList:" + entry.toString());
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
    }
}
