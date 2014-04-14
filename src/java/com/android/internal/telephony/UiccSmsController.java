/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.app.PendingIntent;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsRawData;

import java.util.ArrayList;
import java.util.List;

/**
 * UiccSmsController to provide an inter-process communication to
 * access Sms in Icc.
 */
public class UiccSmsController extends ISms.Stub {
    static final String LOG_TAG = "RIL_UiccSmsController";

    protected Phone[] mPhone;

    protected UiccSmsController(Phone[] phone){
        mPhone = phone;

        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    public boolean
    updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu)
            throws android.os.RemoteException {
        return  updateMessageOnIccEfForSubscriber(getDefaultSmsSubId(), callingPackage,
                index, status, pdu);
    }

    public boolean
    updateMessageOnIccEfForSubscriber(long subId, String callingPackage, int index, int status,
                byte[] pdu) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.updateMessageOnIccEf(callingPackage, index, status, pdu);
        } else {
            Rlog.e(LOG_TAG,"updateMessageOnIccEf iccSmsIntMgr is null" +
                          " for Subscription: " + subId);
            return false;
        }
    }

    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu, byte[] smsc)
            throws android.os.RemoteException {
        return copyMessageToIccEfForSubscriber(getDefaultSmsSubId(), callingPackage, status,
                pdu, smsc);
    }

    public boolean copyMessageToIccEfForSubscriber(long subId, String callingPackage, int status,
            byte[] pdu, byte[] smsc) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.copyMessageToIccEf(callingPackage, status, pdu, smsc);
        } else {
            Rlog.e(LOG_TAG,"copyMessageToIccEf iccSmsIntMgr is null" +
                          " for Subscription: " + subId);
            return false;
        }
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String callingPackage)
            throws android.os.RemoteException {
        return getAllMessagesFromIccEfForSubscriber(getDefaultSmsSubId(), callingPackage);
    }

    public List<SmsRawData> getAllMessagesFromIccEfForSubscriber(long subId, String callingPackage)
                throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getAllMessagesFromIccEf(callingPackage);
        } else {
            Rlog.e(LOG_TAG,"getAllMessagesFromIccEf iccSmsIntMgr is" +
                          " null for Subscription: " + subId);
            return null;
        }
    }

    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
         sendDataForSubscriber(getDefaultSmsSubId(), callingPackage, destAddr, scAddr,
                 destPort, data, sentIntent, deliveryIntent);
    }

    public void sendDataForSubscriber(long subId, String callingPackage, String destAddr,
            String scAddr, int destPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendData(callingPackage, destAddr, scAddr, destPort, data,
                    sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendText iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public void sendDataWithOrigPort(String callingPackage, String destAddr, String scAddr,
            int destPort, int origPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
         sendDataWithOrigPortUsingSubscriber(getDefaultSmsSubId(), callingPackage, destAddr,
                 scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
    }

    public void sendDataWithOrigPortUsingSubscriber(long subId, String callingPackage,
            String destAddr, String scAddr, int destPort, int origPort, byte[] data,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendDataWithOrigPort(callingPackage, destAddr, scAddr, destPort,
                    origPort, data, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendTextWithOrigPort iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public void sendText(String callingPackage, String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendTextForSubscriber(getDefaultSmsSubId(), callingPackage, destAddr, scAddr,
            text, sentIntent, deliveryIntent);
    }

    public void sendTextForSubscriber(long subId, String callingPackage, String destAddr,
            String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendText(callingPackage, destAddr, scAddr, text, sentIntent,
                    deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendText iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public void sendTextWithOptionsUsingSubscriber(long subId, String callingPackage,
            String destAddr, String scAddr, String parts, PendingIntent sentIntents,
            PendingIntent deliveryIntents, int priority, boolean isExpectMore,
            int validityPeriod) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.sendTextWithOptions(callingPackage, destAddr, scAddr, parts, sentIntents,
                    deliveryIntents, priority, isExpectMore, validityPeriod);
        } else {
            Rlog.e(LOG_TAG,"sendTextWithOptions iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }
    public void sendMultipartText(String callingPackage, String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
         sendMultipartTextForSubscriber(getDefaultSmsSubId(), callingPackage, destAddr,
                 scAddr, parts, sentIntents, deliveryIntents);
    }

    public void sendMultipartTextForSubscriber(long subId, String callingPackage, String destAddr,
            String scAddr, List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents)
            throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.sendMultipartText(callingPackage, destAddr, scAddr, parts, sentIntents,
                    deliveryIntents);
        } else {
            Rlog.e(LOG_TAG,"sendMultipartText iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public void sendMultipartTextWithOptionsUsingSubscriber(long subId, String callingPackage,
            String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, int priority, boolean isExpectMore,
            int validityPeriod) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.sendMultipartTextWithOptions(callingPackage, destAddr, scAddr, parts,
                    sentIntents, deliveryIntents, priority, isExpectMore, validityPeriod);
        } else {
            Rlog.e(LOG_TAG,"sendMultipartTextWithOptions iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public boolean enableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return enableCellBroadcastForSubscriber(getDefaultSmsSubId(), messageIdentifier);
    }

    public boolean enableCellBroadcastForSubscriber(long subId, int messageIdentifier)
                throws android.os.RemoteException {
        return enableCellBroadcastRangeForSubscriber(subId, messageIdentifier, messageIdentifier);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return enableCellBroadcastRangeForSubscriber(getDefaultSmsSubId(), startMessageId,
                endMessageId);
    }

    public boolean enableCellBroadcastRangeForSubscriber(long subId, int startMessageId,
            int endMessageId) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.enableCellBroadcastRange(startMessageId, endMessageId);
        } else {
            Rlog.e(LOG_TAG,"enableCellBroadcast iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
        return false;
    }

    public boolean disableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return disableCellBroadcastForSubscriber(getDefaultSmsSubId(), messageIdentifier);
    }

    public boolean disableCellBroadcastForSubscriber(long subId, int messageIdentifier)
                throws android.os.RemoteException {
        return disableCellBroadcastRangeForSubscriber(subId, messageIdentifier, messageIdentifier);
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return disableCellBroadcastRangeForSubscriber(getDefaultSmsSubId(), startMessageId,
                endMessageId);
    }

    public boolean disableCellBroadcastRangeForSubscriber(long subId, int startMessageId,
            int endMessageId) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.disableCellBroadcastRange(startMessageId, endMessageId);
        } else {
            Rlog.e(LOG_TAG,"disableCellBroadcast iccSmsIntMgr is null for" +
                          " Subscription:"+subId);
        }
       return false;
    }

    public int getPremiumSmsPermission(String packageName) {
        return getPremiumSmsPermissionForSubscriber(getDefaultSmsSubId(), packageName);
    }

    @Override
    public int getPremiumSmsPermissionForSubscriber(long subId, String packageName) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.getPremiumSmsPermission(packageName);
        } else {
            Rlog.e(LOG_TAG, "getPremiumSmsPermission iccSmsIntMgr is null");
        }
        //TODO Rakesh
        return 0;
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
         setPremiumSmsPermissionForSubscriber(getDefaultSmsSubId(), packageName, permission);
    }

    @Override
    public void setPremiumSmsPermissionForSubscriber(long subId, String packageName, int permission) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.setPremiumSmsPermission(packageName, permission);
        } else {
            Rlog.e(LOG_TAG, "setPremiumSmsPermission iccSmsIntMgr is null");
        }
    }

    public boolean isImsSmsSupported() {
        return isImsSmsSupportedForSubscriber(getDefaultSmsSubId());
    }

    @Override
    public boolean isImsSmsSupportedForSubscriber(long subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.isImsSmsSupported();
        } else {
            Rlog.e(LOG_TAG, "isImsSmsSupported iccSmsIntMgr is null");
        }
        return false;
    }

    public String getImsSmsFormat() {
        return getImsSmsFormatForSubscriber(getDefaultSmsSubId());
    }

    @Override
    public String getImsSmsFormatForSubscriber(long subId) {
       IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.getImsSmsFormat();
        } else {
            Rlog.e(LOG_TAG, "getImsSmsFormat iccSmsIntMgr is null");
        }
        return null;
    }

    @Override
    public void updateSmsSendStatus(int messageRef, boolean success) {
        getIccSmsInterfaceManager(getDefaultSmsSubId())
            .updateSmsSendStatus(messageRef, success);
    }

    @Override
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        injectSmsPduForSubscriber(getDefaultSmsSubId(), pdu, format, receivedIntent);
    }

    // FIXME: Add injectSmsPduForSubscriber to ISms.aidl
    public void injectSmsPduForSubscriber(long subId, byte[] pdu, String format,
            PendingIntent receivedIntent) {
        getIccSmsInterfaceManager(subId).injectSmsPdu(pdu, format, receivedIntent);
    }

    /**
     * get sms interface manager object based on subscription.
     **/
    private IccSmsInterfaceManager getIccSmsInterfaceManager(long subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId) ;
        //Fixme: for multi-subscription case
        if (!SubscriptionManager.isValidPhoneId(phoneId)
                || phoneId == SubscriptionManager.DEFAULT_PHONE_ID) {
            phoneId = 0;
        }

        try {
            return (IccSmsInterfaceManager)
                ((PhoneProxy)mPhone[phoneId]).getIccSmsInterfaceManager();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace(); //This will print stact trace
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace(); //This will print stack trace
            return null;
        }
    }

    private long getDefaultSmsSubId() {
        return  SubscriptionController.getInstance().getDefaultSmsSubId();
    }

    @Override
    public void sendStoredText(long subId, String callingPkg, Uri messageUri, String scAddress,
            PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendStoredText(callingPkg, messageUri, scAddress, sentIntent,
                    deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendStoredText iccSmsIntMgr is null for subscription: " + subId);
        }
    }

    @Override
    public void sendStoredMultipartText(long subId, String callingPkg, Uri messageUri,
            String scAddress, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents)
            throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.sendStoredMultipartText(callingPkg, messageUri, scAddress, sentIntents,
                    deliveryIntents);
        } else {
            Rlog.e(LOG_TAG,"sendStoredMultipartText iccSmsIntMgr is null for subscription: "
                    + subId);
        }
    }

    /**
     * Get the capacity count of sms on Icc card.
     **/
    public int getSmsCapacityOnIccForSubscriber(long subId)
            throws android.os.RemoteException {
       IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);

        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.getSmsCapacityOnIcc();
        } else {
            Rlog.e(LOG_TAG, "iccSmsIntMgr is null for " + " subId: " + subId);
            return -1;
        }
    }
}
