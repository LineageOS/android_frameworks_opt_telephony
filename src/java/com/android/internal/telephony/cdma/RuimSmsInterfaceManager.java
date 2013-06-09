/*
 * Copyright (C) 2008 The Android Open Source Project
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


package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsRawData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/////////////////////////////////////////////////////////////
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
/////////////////////////////////////////////////////////////


import static android.telephony.SmsManager.STATUS_ON_ICC_FREE;

/**
 * RuimSmsInterfaceManager to provide an inter-process communication to
 * access Sms in Ruim.
 */
public class RuimSmsInterfaceManager extends IccSmsInterfaceManager {
    static final String LOG_TAG = "CDMA";
    static final boolean DBG = true;

    private final Object mLock = new Object();
    private boolean mSuccess;
    private List<SmsRawData> mSms;

    private static final int EVENT_LOAD_DONE = 1;
    private static final int EVENT_UPDATE_DONE = 2;

    //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
    
    protected PrivacySettingsManager pSetMan;
    
    protected static final String P_TAG = "PrivacySMSInterfaceManager";
    
    protected static final int ACCESS_TYPE_SMS_MMS = 0;
	protected static final int ACCESS_TYPE_ICC = 1;
    
    //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
    
	//-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
    /**
     * Gives the actual package names which are trying to send sms
     * {@hide}
     * @return package name array or null
     */
	protected String[] getPackageName(){
		 PackageManager pm = mContext.getPackageManager();
	     String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
	     return packageNames;
	}
    
    /**
     * This method also includes notifications!
     * @param packageNames 
     * @param accessType use constants ACCESS_TYPE_SMS_MMS and ACCESS_TYPE_ICC
     * @return true if package is allowed or exception was thrown or packages are empty, false if package is not allowed 
     * {@hide}
     */
    protected boolean isAllowed(String[] packageNames, int accessType){
    	try{
    		switch(accessType){
    			case ACCESS_TYPE_SMS_MMS:
    				PrivacySettings settings = null;
    	        	if(pSetMan == null) pSetMan = new PrivacySettingsManager(null, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));
    	        	if(pSetMan != null && packageNames != null){
    	        		for(int i=0; i < packageNames.length; i++){
    	            		settings = pSetMan.getSettings(packageNames[i], -1);
    	            		if(pSetMan != null && settings != null && settings.getSmsSendSetting() != PrivacySettings.REAL){
    	            			notify(accessType, packageNames[i],PrivacySettings.EMPTY);
    	            			
    	            			return false;
    	            		}
    	            		settings = null;
    	            	}
    	        		notify(accessType, packageNames[0],PrivacySettings.REAL);
    	        		
    	        		return true;
    	        	}
    	        	else{
    	        		if(packageNames != null && packageNames.length > 0)
    	        			notify(accessType, packageNames[0],PrivacySettings.REAL);
    	     
    	        		return true;
    	        	}
    			case ACCESS_TYPE_ICC:
    				if(pSetMan == null) pSetMan = new PrivacySettingsManager(null, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));
    	        	if(pSetMan != null && packageNames != null){
    	        		for(int i=0; i < packageNames.length; i++){
    	            		settings = pSetMan.getSettings(packageNames[i], -1);
    	            		if(pSetMan != null && settings != null && settings.getIccAccessSetting() != PrivacySettings.REAL){
    	            			notify(accessType, packageNames[i],PrivacySettings.EMPTY);
    	            			return false;
    	            		}
    	            		settings = null;
    	            	}
    	        		notify(accessType, packageNames[0],PrivacySettings.REAL);
    	        		return true;
    	        	}
    	        	else{
    	        		if(packageNames != null && packageNames.length > 0)
    	        			notify(accessType, packageNames[0],PrivacySettings.REAL);
    	        			
    	        		return true;
    	        	}
    			default:
    	        	notify(accessType, packageNames[0],PrivacySettings.REAL);
    	        	return true;
    		}
    		
    	}
    	catch(Exception e){
    		Log.e(P_TAG,"Got exception while checking for sms or ICC acess permission");
    		e.printStackTrace();
    		if(packageNames != null && pSetMan != null && packageNames.length > 0){
    			PrivacySettings settings = pSetMan.getSettings(packageNames[0], -1);
    			if(settings != null)
    				notify(accessType, packageNames[0],PrivacySettings.REAL);
    		}
    		return true;
    	}
    }
    
    /**
     * {@hide}
     * Helper method for method isAllowed() to show dataAccess toasts
     * @param accessType use ACCESS_TYPE_SMS_MMS or ACCESS_TYPE_ICC
     * @param packageName the package name
     * @param accessMode PrivacySettings.REAL || PrivacySettings.CUSTOM || PrivacySettings.RANDOM || PrivacySettings.EMPTY
     */
    protected void notify(int accessType,String packageName, byte accessMode){
    	switch(accessType){
    		case ACCESS_TYPE_SMS_MMS:
    			//Log.i("PrivacySmsManager","now send notify information outgoing sms");
    			pSetMan.notification(packageName, 0, accessMode, PrivacySettings.DATA_SMS_SEND, null, null);
    			break;
    		case ACCESS_TYPE_ICC:
    			//Log.i("PrivacySmsManager","now send notify information ICC ACCESS");
    			pSetMan.notification(packageName, 0, accessMode, PrivacySettings.DATA_ICC_ACCESS, null, null);
    			break;
    	}
    }
    
    //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_UPDATE_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        mSuccess = (ar.exception == null);
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_LOAD_DONE:
                    ar = (AsyncResult)msg.obj;
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            mSms = buildValidRawData((ArrayList<byte[]>) ar.result);
                        } else {
                            if(DBG) log("Cannot load Sms records");
                            if (mSms != null)
                                mSms.clear();
                        }
                        mLock.notifyAll();
                    }
                    break;
            }
        }
    };

    public RuimSmsInterfaceManager(CDMAPhone phone, SMSDispatcher dispatcher) {
        super(phone);
        mDispatcher = dispatcher;
    }

    public void dispose() {
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        if(DBG) Log.d(LOG_TAG, "RuimSmsInterfaceManager finalized");
    }

    /**
     * Update the specified message on the RUIM.
     *
     * @param index record index of message to update
     * @param status new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @return success or not
     *
     */
    public boolean
    updateMessageOnIccEf(int index, int status, byte[] pdu) {
        if (DBG) log("updateMessageOnIccEf: index=" + index +
                " status=" + status + " ==> " +
                "("+ pdu + ")");
        
        //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
        if(!isAllowed(getPackageName(),ACCESS_TYPE_ICC)){
        	return false;
        }
        //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
        
        enforceReceiveAndSend("Updating message on RUIM");
        synchronized(mLock) {
            mSuccess = false;
            Message response = mHandler.obtainMessage(EVENT_UPDATE_DONE);

            if (status == STATUS_ON_ICC_FREE) {
                // Special case FREE: call deleteSmsOnRuim instead of
                // manipulating the RUIM record
                mPhone.mCM.deleteSmsOnRuim(index, response);
            } else {
                byte[] record = makeSmsRecordData(status, pdu);
                mPhone.getIccFileHandler().updateEFLinearFixed(
                        IccConstants.EF_SMS, index, record, null, response);
            }
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        return mSuccess;
    }

    /**
     * Copy a raw SMS PDU to the RUIM.
     *
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @return success or not
     *
     */
    public boolean copyMessageToIccEf(int status, byte[] pdu, byte[] smsc) {
        //NOTE smsc not used in RUIM
        if (DBG) log("copyMessageToIccEf: status=" + status + " ==> " +
                "pdu=("+ Arrays.toString(pdu) + ")");
        
        //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
        if(!isAllowed(getPackageName(),ACCESS_TYPE_ICC)){
        	return false;
        }
        //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
        
        enforceReceiveAndSend("Copying message to RUIM");
        synchronized(mLock) {
            mSuccess = false;
            Message response = mHandler.obtainMessage(EVENT_UPDATE_DONE);

            mPhone.mCM.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu),
                    response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        return mSuccess;
    }

    /**
     * Retrieves all messages currently stored on RUIM.
     */
    public List<SmsRawData> getAllMessagesFromIccEf() {
        if (DBG) log("getAllMessagesFromEF");

        //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
        if(!isAllowed(getPackageName(),ACCESS_TYPE_ICC)){
        	return new ArrayList<SmsRawData>();
        }
        //-------------------------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----------------------------------------------------
        
        Context context = mPhone.getContext();

        context.enforceCallingPermission(
                "android.permission.RECEIVE_SMS",
                "Reading messages from RUIM");
        synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_LOAD_DONE);
            mPhone.getIccFileHandler().loadEFLinearFixedAll(IccConstants.EF_SMS, response);

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to load from the RUIM");
            }
        }
        return mSms;
    }

    public boolean enableCellBroadcast(int messageIdentifier) {
        // Not implemented
        Log.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    public boolean disableCellBroadcast(int messageIdentifier) {
        // Not implemented
        Log.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        // Not implemented
        Log.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        // Not implemented
        Log.e(LOG_TAG, "Error! Not implemented for CDMA.");
        return false;
    }

    protected void log(String msg) {
        Log.d(LOG_TAG, "[RuimSmsInterfaceManager] " + msg);
    }
    
}

