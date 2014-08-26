/*
 * Copyright (C) 2014 MediaTek Inc.
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

package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.Messenger;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.net.NetworkRequest;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.AsyncChannel;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.SubscriptionController;

import android.util.Log;
import java.util.HashSet;
import java.util.Iterator;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;

public class DctController extends Handler {
    private static final String LOG_TAG = "DctController";
    private static final boolean DBG = true;

    private static final int EVENT_PHONE1_DETACH = 1;
    private static final int EVENT_PHONE2_DETACH = 2;
    private static final int EVENT_PHONE3_DETACH = 3;
    private static final int EVENT_PHONE4_DETACH = 4;
    private static final int EVENT_PHONE1_RADIO_OFF = 5;
    private static final int EVENT_PHONE2_RADIO_OFF = 6;
    private static final int EVENT_PHONE3_RADIO_OFF = 7;
    private static final int EVENT_PHONE4_RADIO_OFF = 8;
    private static final int EVENT_START_DDS_SWITCH = 9;

    private static final int PHONE_NONE = -1;

    private static DctController sDctController;

    private static final int EVENT_ALL_DATA_DISCONNECTED = 1;
    private static final int EVENT_SET_DATA_ALLOW_DONE = 2;

    private RegistrantList mNotifyDefaultDataSwitchInfo = new RegistrantList();
    private RegistrantList mNotifyOnDemandDataSwitchInfo = new RegistrantList();
    private SubscriptionController mSubController = SubscriptionController.getInstance();

    private Phone mActivePhone;
    private int mPhoneNum;
    private boolean[] mServicePowerOffFlag;
    private PhoneProxy[] mPhones;
    private DcSwitchState[] mDcSwitchState;
    private DcSwitchAsyncChannel[] mDcSwitchAsyncChannel;
    private Handler[] mDcSwitchStateHandler;

    private HashSet<String> mApnTypes = new HashSet<String>();

    private BroadcastReceiver mDataStateReceiver;
    private Context mContext;

    private AsyncChannel mDdsSwitchPropService;

    private int mCurrentDataPhone = PHONE_NONE;
    private int mRequestedDataPhone = PHONE_NONE;

    private DdsSwitchSerializerHandler mDdsSwitchSerializer;
    private boolean mIsDdsSwitchCompleted = true;

    private Handler mRspHander = new Handler() {
        public void handleMessage(Message msg){
            AsyncResult ar;
            switch(msg.what) {
                case EVENT_PHONE1_DETACH:
                case EVENT_PHONE2_DETACH:
                case EVENT_PHONE3_DETACH:
                case EVENT_PHONE4_DETACH:
                    logd("EVENT_PHONE" + msg.what +
                            "_DETACH: mRequestedDataPhone=" + mRequestedDataPhone);
                    mCurrentDataPhone = PHONE_NONE;
                    if (mRequestedDataPhone != PHONE_NONE) {
                        mCurrentDataPhone = mRequestedDataPhone;
                        mRequestedDataPhone = PHONE_NONE;

                        Iterator<String> itrType = mApnTypes.iterator();
                        while (itrType.hasNext()) {
                            mDcSwitchAsyncChannel[mCurrentDataPhone].connectSync(itrType.next());
                        }
                        mApnTypes.clear();
                    }
                break;

                case EVENT_PHONE1_RADIO_OFF:
                case EVENT_PHONE2_RADIO_OFF:
                case EVENT_PHONE3_RADIO_OFF:
                case EVENT_PHONE4_RADIO_OFF:
                    logd("EVENT_PHONE" + (msg.what - EVENT_PHONE1_RADIO_OFF + 1) + "_RADIO_OFF.");
                    mServicePowerOffFlag[msg.what - EVENT_PHONE1_RADIO_OFF] = true;
                break;

                default:
                break;
            }
        }
    };

    private DefaultPhoneNotifier.IDataStateChangedCallback mDataStateChangedCallback =
            new DefaultPhoneNotifier.IDataStateChangedCallback() {
        public void onDataStateChanged(long subId, String state, String reason,
                String apnName, String apnType, boolean unavailable) {
            logd("[DataStateChanged]:" + "state=" + state + ",reason=" + reason
                      + ",apnName=" + apnName + ",apnType=" + apnType + ",from subId=" + subId);
            int phoneId = SubscriptionManager.getPhoneId(subId);
            mDcSwitchState[phoneId].notifyDataConnection(phoneId, state, reason,
                    apnName, apnType, unavailable);
        }
    };

    private class DataStateReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            synchronized(this) {
                if (intent.getAction().equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                    ServiceState ss = ServiceState.newFromBundle(intent.getExtras());

                    long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, PhoneConstants.SUB1);
                    int phoneId = SubscriptionManager.getPhoneId(subId);
                    logd("DataStateReceiver: phoneId= " + phoneId);

                    // for the case of network out of service when bootup (ignore dummy values too)
                    if (!SubscriptionManager.isValidSubId(subId) || (subId < 0)) {
                        // FIXME: Maybe add SM.isRealSubId(subId)??
                        logd("DataStateReceiver: ignore invalid subId=" + subId);
                        return;
                    }
                    if (!SubscriptionManager.isValidPhoneId(phoneId)) {
                        logd("DataStateReceiver: ignore invalid phoneId=" + phoneId);
                        return;
                    }

                    boolean prevPowerOff = mServicePowerOffFlag[phoneId];
                    if (ss != null) {
                        int state = ss.getState();
                        switch (state) {
                            case ServiceState.STATE_POWER_OFF:
                                mServicePowerOffFlag[phoneId] = true;
                                logd("DataStateReceiver: STATE_POWER_OFF Intent from phoneId="
                                        + phoneId);
                                break;
                            case ServiceState.STATE_IN_SERVICE:
                                mServicePowerOffFlag[phoneId] = false;
                                logd("DataStateReceiver: STATE_IN_SERVICE Intent from phoneId="
                                        + phoneId);
                                break;
                            case ServiceState.STATE_OUT_OF_SERVICE:
                                logd("DataStateReceiver: STATE_OUT_OF_SERVICE Intent from phoneId="
                                        + phoneId);
                                if (mServicePowerOffFlag[phoneId]) {
                                    mServicePowerOffFlag[phoneId] = false;
                                }
                                break;
                            case ServiceState.STATE_EMERGENCY_ONLY:
                                logd("DataStateReceiver: STATE_EMERGENCY_ONLY Intent from phoneId="
                                        + phoneId);
                                break;
                            default:
                                logd("DataStateReceiver: SERVICE_STATE_CHANGED invalid state");
                                break;
                        }

                        if (prevPowerOff && mServicePowerOffFlag[phoneId] == false &&
                                mCurrentDataPhone == PHONE_NONE &&
                                phoneId == getDataConnectionFromSetting()) {
                            logd("DataStateReceiver: Current Phone is none and default phoneId="
                                    + phoneId + ", then enableApnType()");
                            enableApnType(subId, PhoneConstants.APN_TYPE_DEFAULT);
                        }
                    }
                }
            }
        }
    }

    public DefaultPhoneNotifier.IDataStateChangedCallback getDataStateChangedCallback() {
        return mDataStateChangedCallback;
    }

    public static DctController getInstance() {
       if (sDctController == null) {
        throw new RuntimeException(
            "DCTrackerController.getInstance can't be called before makeDCTController()");
        }
       return sDctController;
    }

    public static DctController makeDctController(PhoneProxy[] phones, Looper looper) {
        if (sDctController == null) {
            sDctController = new DctController(phones, looper);
        }
        return sDctController;
    }

    private DctController(PhoneProxy[] phones, Looper looper) {
        super(looper);
        if (phones == null || phones.length == 0) {
            if (phones == null) {
                loge("DctController(phones): UNEXPECTED phones=null, ignore");
            } else {
                loge("DctController(phones): UNEXPECTED phones.length=0, ignore");
            }
            return;
        }
        mPhoneNum = phones.length;
        mServicePowerOffFlag = new boolean[mPhoneNum];
        mPhones = phones;

        mDcSwitchState = new DcSwitchState[mPhoneNum];
        mDcSwitchAsyncChannel = new DcSwitchAsyncChannel[mPhoneNum];
        mDcSwitchStateHandler = new Handler[mPhoneNum];

        mActivePhone = mPhones[0];

        for (int i = 0; i < mPhoneNum; ++i) {
            int phoneId = i;
            mServicePowerOffFlag[i] = true;
            mDcSwitchState[i] = new DcSwitchState(mPhones[i], "DcSwitchState-" + phoneId, phoneId);
            mDcSwitchState[i].start();
            mDcSwitchAsyncChannel[i] = new DcSwitchAsyncChannel(mDcSwitchState[i], phoneId);
            mDcSwitchStateHandler[i] = new Handler();

            int status = mDcSwitchAsyncChannel[i].fullyConnectSync(mPhones[i].getContext(),
                mDcSwitchStateHandler[i], mDcSwitchState[i].getHandler());

            if (status == AsyncChannel.STATUS_SUCCESSFUL) {
                logd("DctController(phones): Connect success: " + i);
            } else {
                loge("DctController(phones): Could not connect to " + i);
            }

            mDcSwitchState[i].registerForIdle(mRspHander, EVENT_PHONE1_DETACH + i, null);

            // Register for radio state change
            PhoneBase phoneBase = (PhoneBase)((PhoneProxy)mPhones[i]).getActivePhone();
            phoneBase.mCi.registerForOffOrNotAvailable(mRspHander, EVENT_PHONE1_RADIO_OFF + i, null);
        }

        mContext = mActivePhone.getContext();

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
        filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);

        mDataStateReceiver = new DataStateReceiver();
        Intent intent = mContext.registerReceiver(mDataStateReceiver, filter);

        HandlerThread t = new HandlerThread("DdsSwitchSerializer");
        t.start();

        mDdsSwitchSerializer = new DdsSwitchSerializerHandler(t.getLooper());

    }

    private IccCardConstants.State getIccCardState(int phoneId) {
        return mPhones[phoneId].getIccCard().getState();
    }

    /**
     * Enable PDP interface by apn type and phone id
     *
     * @param type enable pdp interface by apn type, such as PhoneConstants.APN_TYPE_MMS, etc.
     * @param subId Indicate which sub to query
     * @return PhoneConstants.APN_REQUEST_STARTED: action is already started
     * PhoneConstants.APN_ALREADY_ACTIVE: interface has already active
     * PhoneConstants.APN_TYPE_NOT_AVAILABLE: invalid APN type
     * PhoneConstants.APN_REQUEST_FAILED: request failed
     * PhoneConstants.APN_REQUEST_FAILED_DUE_TO_RADIO_OFF: readio turn off
     * @see #disableApnType()
     */
    public synchronized int enableApnType(long subId, String type) {
        int phoneId = SubscriptionManager.getPhoneId(subId);

        if (phoneId == PHONE_NONE || !isValidphoneId(phoneId)) {
            logw("enableApnType(): with PHONE_NONE or Invalid PHONE ID");
            return PhoneConstants.APN_REQUEST_FAILED;
        }

        logd("enableApnType():type=" + type + ",phoneId=" + phoneId +
                ",powerOff=" + mServicePowerOffFlag[phoneId]);

        if (!PhoneConstants.APN_TYPE_DEFAULT.equals(type)) {
            for (int peerphoneId =0; peerphoneId < mPhoneNum; peerphoneId++) {
                // check peer Phone has non default APN activated as receiving non default APN request.
                if (phoneId == peerphoneId) {
                    continue;
                }

                String[] activeApnTypes = mPhones[peerphoneId].getActiveApnTypes();
                if (activeApnTypes != null && activeApnTypes.length != 0) {
                    for (int i=0; i<activeApnTypes.length; i++) {
                        if (!PhoneConstants.APN_TYPE_DEFAULT.equals(activeApnTypes[i]) &&
                                mPhones[peerphoneId].getDataConnectionState(activeApnTypes[i]) !=
                                PhoneConstants.DataState.DISCONNECTED) {
                            logd("enableApnType:Peer Phone still have non-default active APN type:"+
                                    "activeApnTypes[" + i + "]=" + activeApnTypes[i]);
                            return PhoneConstants.APN_REQUEST_FAILED;
                        }
                    }
                }
            }
        }

        logd("enableApnType(): CurrentDataPhone=" +
                    mCurrentDataPhone + ", RequestedDataPhone=" + mRequestedDataPhone);

        if (phoneId == mCurrentDataPhone &&
               !mDcSwitchAsyncChannel[mCurrentDataPhone].isIdleOrDeactingSync()) {
           mRequestedDataPhone = PHONE_NONE;
           logd("enableApnType(): mRequestedDataPhone equals request PHONE ID.");
           return mDcSwitchAsyncChannel[phoneId].connectSync(type);
        } else {
            // Only can switch data when mCurrentDataPhone is PHONE_NONE,
            // it is set to PHONE_NONE only as receiving EVENT_PHONEX_DETACH
            if (mCurrentDataPhone == PHONE_NONE) {
                mCurrentDataPhone = phoneId;
                mRequestedDataPhone = PHONE_NONE;
                logd("enableApnType(): current PHONE is NONE or IDLE, mCurrentDataPhone=" +
                        mCurrentDataPhone);
                return mDcSwitchAsyncChannel[phoneId].connectSync(type);
            } else {
                logd("enableApnType(): current PHONE:" + mCurrentDataPhone + " is active.");
                if (phoneId != mRequestedDataPhone) {
                    mApnTypes.clear();
                }
                mApnTypes.add(type);
                mRequestedDataPhone = phoneId;
                mDcSwitchState[mCurrentDataPhone].cleanupAllConnection();
            }
        }
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    /**
     * disable PDP interface by apn type and sub id
     *
     * @param type enable pdp interface by apn type, such as PhoneConstants.APN_TYPE_MMS, etc.
     * @param subId Indicate which sub to query
     * @return PhoneConstants.APN_REQUEST_STARTED: action is already started
     * PhoneConstants.APN_ALREADY_ACTIVE: interface has already active
     * PhoneConstants.APN_TYPE_NOT_AVAILABLE: invalid APN type
     * PhoneConstants.APN_REQUEST_FAILED: request failed
     * PhoneConstants.APN_REQUEST_FAILED_DUE_TO_RADIO_OFF: readio turn off
     * @see #enableApnTypeGemini()
     */
    public synchronized int disableApnType(long subId, String type) {

        int phoneId = SubscriptionManager.getPhoneId(subId);

        if (phoneId == PHONE_NONE || !isValidphoneId(phoneId)) {
            logw("disableApnType(): with PHONE_NONE or Invalid PHONE ID");
            return PhoneConstants.APN_REQUEST_FAILED;
        }
        logd("disableApnType():type=" + type + ",phoneId=" + phoneId +
                ",powerOff=" + mServicePowerOffFlag[phoneId]);
        return mDcSwitchAsyncChannel[phoneId].disconnectSync(type);
    }

    public boolean isDataConnectivityPossible(String type, int phoneId) {
        if (phoneId == PHONE_NONE || !isValidphoneId(phoneId)) {
            logw("isDataConnectivityPossible(): with PHONE_NONE or Invalid PHONE ID");
            return false;
        } else {
            return mPhones[phoneId].isDataConnectivityPossible(type);
        }
    }

    public boolean isIdleOrDeacting(int phoneId) {
        if (mDcSwitchAsyncChannel[phoneId].isIdleOrDeactingSync()) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isValidphoneId(int phoneId) {
        return phoneId >= 0 && phoneId < mPhoneNum;
    }

    private boolean isValidApnType(String apnType) {
         if (apnType.equals(PhoneConstants.APN_TYPE_DEFAULT)
             || apnType.equals(PhoneConstants.APN_TYPE_MMS)
             || apnType.equals(PhoneConstants.APN_TYPE_SUPL)
             || apnType.equals(PhoneConstants.APN_TYPE_DUN)
             || apnType.equals(PhoneConstants.APN_TYPE_HIPRI)
             || apnType.equals(PhoneConstants.APN_TYPE_FOTA)
             || apnType.equals(PhoneConstants.APN_TYPE_IMS)
             || apnType.equals(PhoneConstants.APN_TYPE_CBS))
        {
            return true;
        } else {
            return false;
        }
    }

    private int getDataConnectionFromSetting(){
        long subId = mSubController.getDefaultDataSubId();
        int phoneId = SubscriptionManager.getPhoneId(subId);
        return phoneId;
    }

    private static void logv(String s) {
        Log.v(LOG_TAG, "[DctController] " + s);
    }

    private static void logd(String s) {
        Log.d(LOG_TAG, "[DctController] " + s);
    }

    private static void logw(String s) {
        Log.w(LOG_TAG, "[DctController] " + s);
    }

    private static void loge(String s) {
        Log.e(LOG_TAG, "[DctController] " + s);
    }

    private class SwitchInfo {
        public int mPhoneId;
        public NetworkRequest mNetworkRequest;
        public boolean mIsDefaultDataSwitchRequested;

        public SwitchInfo(int phoneId, NetworkRequest n, boolean flag) {
            mPhoneId = phoneId;
            mNetworkRequest = n;
            mIsDefaultDataSwitchRequested = flag;
        }

        public SwitchInfo(int phoneId,boolean flag) {
            mPhoneId = phoneId;
            mNetworkRequest = null;
            mIsDefaultDataSwitchRequested = flag;
        }
        public String toString() {
            return "SwitchInfo[phoneId = " + mPhoneId
                + ", NetworkRequest =" +mNetworkRequest
                + ", isDefaultSwitchRequested = " + mIsDefaultDataSwitchRequested;
        }
    }

    public void setDefaultDataSubId(long subId) {
        Rlog.d(LOG_TAG, "setDataAllowed subId :" + subId);
        int phoneId = mSubController.getPhoneId(subId);
        int prefPhoneId = mSubController.getPhoneId(mSubController.getCurrentDds());
        if (prefPhoneId < 0 || prefPhoneId >= mPhoneNum) {
            // If Current dds subId is invalid set the received subId as curretn DDS
            // and return from here.
            // DcSwitchState will take care of sending allowData on latet dds subId
            // once it receives valid data registration state
            logd(" setDefaultDataSubId,  subId = " + subId + " phoneId  " + prefPhoneId);
            Settings.Global.putLong(mContext.getContentResolver(),
                    Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION, subId);
            return;
        }

        Phone phone = mPhones[prefPhoneId].getActivePhone();
        DcTrackerBase dcTracker =((PhoneBase)phone).mDcTracker;
        dcTracker.setDataAllowed(false, null);
        SwitchInfo s = new SwitchInfo(new Integer(phoneId), true);
        mPhones[prefPhoneId].registerForAllDataDisconnected(
                this, EVENT_ALL_DATA_DISCONNECTED, s);
    }

    public void setOnDemandDataSubId(NetworkRequest n) {
        Rlog.d(LOG_TAG, "setDataAllowed for :" + n);
        mDdsSwitchSerializer.sendMessage(mDdsSwitchSerializer
                .obtainMessage(EVENT_START_DDS_SWITCH, n));
    }

    public void registerForDefaultDataSwitchInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        synchronized (mNotifyDefaultDataSwitchInfo) {
            mNotifyDefaultDataSwitchInfo.add(r);
        }
    }

    public void registerForOnDemandDataSwitchInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        synchronized (mNotifyOnDemandDataSwitchInfo) {
            mNotifyOnDemandDataSwitchInfo.add(r);
        }
    }

    public void registerDdsSwitchPropService(Messenger messenger) {
        logd("Got messenger from DDS switch service, messenger = " + messenger);
        AsyncChannel ac = new AsyncChannel();
        ac.connect(mContext, sDctController, messenger);
    }

    @Override
        public void handleMessage (Message msg) {
            Rlog.d(LOG_TAG, "handleMessage msg=" + msg);

            switch (msg.what) {
                case EVENT_ALL_DATA_DISCONNECTED: {
                    AsyncResult ar = (AsyncResult)msg.obj;
                    SwitchInfo s = (SwitchInfo)ar.userObj;
                    Integer phoneId = s.mPhoneId;
                    int prefPhoneId = mSubController.getPhoneId(
                             mSubController.getCurrentDds());
                    Rlog.d(LOG_TAG, "EVENT_ALL_DATA_DISCONNECTED switchInfo :" + s);
                    mPhones[prefPhoneId].unregisterForAllDataDisconnected(this);
                    Message allowedDataDone = Message.obtain(this,
                            EVENT_SET_DATA_ALLOW_DONE, s);
                    Phone phone = mPhones[phoneId].getActivePhone();

                    if (mDdsSwitchPropService != null) {

                        logd("Request OemHookDDS service for DDS switch");
                        mDdsSwitchPropService.sendMessageSynchronously(1, phoneId,
                                mPhoneNum);
                        logd("OemHookDDS service finished");
                    }

                    DcTrackerBase dcTracker =((PhoneBase)phone).mDcTracker;
                    dcTracker.setDataAllowed(true, allowedDataDone);

                   break;
                }

                case EVENT_SET_DATA_ALLOW_DONE: {
                    AsyncResult ar = (AsyncResult)msg.obj;
                    SwitchInfo s = (SwitchInfo)ar.userObj;
                    Integer phoneId = s.mPhoneId;
                    long[] subId = mSubController.getSubId(phoneId);
                    Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOWED_DONE  phoneId :" + subId[0]
                            + ", switchInfo = " + s);

                    if(ar.exception != null) {
                        Rlog.d(LOG_TAG, "Retry, switchInfo = " + s);
                        Message allowedDataDone = Message.obtain(this,
                                EVENT_SET_DATA_ALLOW_DONE, s);
                        Phone phone = mPhones[phoneId].getActivePhone();
                        DcTrackerBase dcTracker =((PhoneBase)phone).mDcTracker;
                        dcTracker.setDataAllowed(true, allowedDataDone);
                        break;
                    }
                    mDdsSwitchSerializer.unLock();

                    if (s.mIsDefaultDataSwitchRequested) {
                        mNotifyDefaultDataSwitchInfo.notifyRegistrants(
                                new AsyncResult(null, subId[0], null));
                    } else {
                        mNotifyOnDemandDataSwitchInfo.notifyRegistrants(
                                new AsyncResult(null, s.mNetworkRequest, null));
                    }
                    break;
                }
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED: {
                    if(msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        logd("HALF_CONNECTED: Connection successful with DDS switch"
                                + " service");
                        mDdsSwitchPropService = (AsyncChannel) msg.obj;
                    } else {
                        logd("HALF_CONNECTED: Connection failed with"
                                +" DDS switch service, err = " + msg.arg1);
                    }
                       break;
                }

                case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                    logd("Connection disconnected with DDS switch service");
                    mDdsSwitchPropService = null;
                    break;
                }
        }
    }

    class DdsSwitchSerializerHandler extends Handler {
        final static String TAG = "DdsSwitchSerializer";

        public DdsSwitchSerializerHandler(Looper looper) {
            super(looper);
        }

        public void unLock() {
            Rlog.d(TAG, "unLock the DdsSwitchSerializer");
            synchronized(this) {
                mIsDdsSwitchCompleted = true;
                Rlog.d(TAG, "unLocked the DdsSwitchSerializer");
                notifyAll();
            }

        }

        public boolean isLocked() {
            synchronized(this) {
                Rlog.d(TAG, "isLocked = " + !mIsDdsSwitchCompleted);
                return !mIsDdsSwitchCompleted;
            }

        }

        @Override
        public void handleMessage (Message msg) {
            switch(msg.what) {
                case EVENT_START_DDS_SWITCH: {
                    Rlog.d(TAG, "EVENT_START_DDS_SWITCH");

                    try {
                        synchronized(this) {
                            while(!mIsDdsSwitchCompleted) {
                                Rlog.d(TAG, "DDS switch in progress, wait");
                                wait();
                            }

                            Rlog.d(TAG, "Locked!");
                            mIsDdsSwitchCompleted = false;
                        }
                    } catch (Exception e) {
                        Rlog.d(TAG, "Exception while serializing the DDS"
                                + " switch request , e=" + e);
                        return;
                    }

                    NetworkRequest n = (NetworkRequest)msg.obj;

                    Rlog.d(TAG, "start the DDS switch for req " + n);
                    long subId = mSubController.getSubIdFromNetworkRequest(n);

                    if(subId == mSubController.getCurrentDds()) {
                        Rlog.d(TAG, "No change in DDS, respond back");
                        mIsDdsSwitchCompleted = true;
                        mNotifyOnDemandDataSwitchInfo.notifyRegistrants(
                                new AsyncResult(null, n, null));
                        return;
                    }
                    int phoneId = mSubController.getPhoneId(subId);
                    int prefPhoneId = mSubController.getPhoneId(
                            mSubController.getCurrentDds());
                    Phone phone = mPhones[prefPhoneId].getActivePhone();
                    DcTrackerBase dcTracker =((PhoneBase)phone).mDcTracker;
                    dcTracker.setDataAllowed(false, null);
                    SwitchInfo s = new SwitchInfo(new Integer(phoneId), n, false);
                    mPhones[prefPhoneId].registerForAllDataDisconnected(
                            sDctController, EVENT_ALL_DATA_DISCONNECTED, s);


                    break;
                }
            }
        }
    }
    public boolean isDctControllerLocked() {
        return mDdsSwitchSerializer.isLocked();
    }
}
