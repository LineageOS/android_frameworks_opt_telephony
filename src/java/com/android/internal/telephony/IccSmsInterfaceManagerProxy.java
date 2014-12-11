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

package com.android.internal.telephony;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.util.Log;

public class IccSmsInterfaceManagerProxy extends ISms.Stub {
    static final String LOG_TAG = "IccSmsInterfaceManagerProxy";

    private IccSmsInterfaceManager mIccSmsInterfaceManager;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // check if the message was aborted
            if (getResultCode() != Activity.RESULT_OK) {
                return;
            }
            String destAddr = getResultData();
            String scAddr = intent.getStringExtra("scAddr");
            String callingPackage = intent.getStringExtra("callingPackage");
            ArrayList<String> parts = intent.getStringArrayListExtra("parts");
            ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");
            ArrayList<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents");

            if (intent.getIntExtra("callingUid", 0) != 0) {
                callingPackage = callingPackage + "\\" + intent.getIntExtra("callingUid", 0);
            }

            if (intent.getBooleanExtra("multipart", false)) {
                if (Rlog.isLoggable("SMS", Log.VERBOSE)) {
                    log("ProxiedMultiPartSms destAddr: " + destAddr +
                            "\n scAddr=" + scAddr +
                            "\n callingPackage=" + callingPackage +
                            "\n partsSize=" + parts.size());
                }
                mIccSmsInterfaceManager.sendMultipartText(callingPackage, destAddr, scAddr,
                        parts, sentIntents, deliveryIntents);
                return;
            }

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }
            String text = null;
            if (parts != null && parts.size() > 0) {
                text = parts.get(0);
            }
            if (Rlog.isLoggable("SMS", Log.VERBOSE)) {
                log("ProxiedSms destAddr: " + destAddr +
                        "\n scAddr=" + scAddr +
                        "\n callingPackage=" + callingPackage);
            }
            mIccSmsInterfaceManager.sendText(callingPackage, destAddr, scAddr, text,
                    sentIntent, deliveryIntent);
        }
    };

    public IccSmsInterfaceManagerProxy(Context context,
            IccSmsInterfaceManager iccSmsInterfaceManager) {
        this.mContext = context;
        mIccSmsInterfaceManager = iccSmsInterfaceManager;
        if(ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }

        createWakelock();
    }

    public void setmIccSmsInterfaceManager(IccSmsInterfaceManager iccSmsInterfaceManager) {
        mIccSmsInterfaceManager = iccSmsInterfaceManager;
    }

    private void createWakelock() {
        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IccSmsInterfaceManager");
        mWakeLock.setReferenceCounted(true);
    }

    private long getDefaultSmsSubId() {
        return SubscriptionController.getInstance().getDefaultSmsSubId();
    }

    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private static final int WAKE_LOCK_TIMEOUT = 5000;
    private final Handler mHandler = new Handler();
    private void dispatchPdus(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);
        // Direct the intent to only the default SMS app. If we can't find a default SMS app
        // then send it to all broadcast receivers.
        ComponentName componentName = SmsApplication.getDefaultSmsApplication(mContext, true);
        if (componentName == null)
            return;

        if (Rlog.isLoggable("SMS", Log.VERBOSE)) {
            log("dispatchPdu pdus: " + pdus +
                    "\n componentName=" + componentName +
                    "\n format=" + SmsMessage.FORMAT_SYNTHETIC);
        }

        // Deliver SMS message only to this receiver
        intent.setComponent(componentName);
        intent.putExtra("pdus", pdus);
        intent.putExtra("format", SmsMessage.FORMAT_SYNTHETIC);
        dispatch(intent, Manifest.permission.RECEIVE_SMS);

        intent.setAction(Intents.SMS_RECEIVED_ACTION);
        intent.setComponent(null);
        dispatch(intent, Manifest.permission.RECEIVE_SMS);
    }

    private void dispatch(Intent intent, String permission) {
        // Hold a wake lock for WAKE_LOCK_TIMEOUT seconds, enough to give any
        // receivers time to take their own wake locks.
        mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
        intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);
        mContext.sendOrderedBroadcast(intent, permission, AppOpsManager.OP_RECEIVE_SMS, null,
                mHandler, Activity.RESULT_OK, null, null);
    }

    public void synthesizeMessages(
            String originatingAddress, String scAddress, List<String> messages,
            long timestampMillis) throws RemoteException {
        mContext.enforceCallingPermission(
                android.Manifest.permission.BROADCAST_SMS, "");
        byte[][] pdus = new byte[messages.size()][];
        for (int i = 0; i < messages.size(); i++) {
            SyntheticSmsMessage message = new SyntheticSmsMessage(originatingAddress,
                    scAddress, messages.get(i), timestampMillis);
            pdus[i] = message.getPdu();
        }
        dispatchPdus(pdus);
    }

    @Override
    public boolean
    updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu) {
         return mIccSmsInterfaceManager.updateMessageOnIccEf(callingPackage, index, status, pdu);
    }

    @Override
    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu,
            byte[] smsc) {
        return mIccSmsInterfaceManager.copyMessageToIccEf(callingPackage, status, pdu, smsc);
    }

    @Override
    public List<SmsRawData> getAllMessagesFromIccEf(String callingPackage) {
        return mIccSmsInterfaceManager.getAllMessagesFromIccEf(callingPackage);
    }

    @Override
    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent)
            throws RemoteException {
        sendDataForSubscriber(getDefaultSmsSubId(), callingPackage, destAddr, scAddr, destPort,
                data, sentIntent, deliveryIntent);
    }

    private void broadcastOutgoingSms(
            String callingPackage, String destAddr, String scAddr, boolean multipart,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents, int priority, boolean isExpectMore,
            int validityPeriod) {
        Intent broadcast = new Intent(Intent.ACTION_NEW_OUTGOING_SMS);
        broadcast.putExtra("destAddr", destAddr);
        broadcast.putExtra("scAddr", scAddr);
        broadcast.putExtra("multipart", multipart);
        broadcast.putExtra("callingPackage", callingPackage);
        broadcast.putExtra("callingUid", android.os.Binder.getCallingUid());
        broadcast.putStringArrayListExtra("parts", parts);
        broadcast.putParcelableArrayListExtra("sentIntents", sentIntents);
        broadcast.putParcelableArrayListExtra("deliveryIntents", deliveryIntents);
        broadcast.putExtra("priority", priority);
        broadcast.putExtra("isExpectMore", isExpectMore);
        broadcast.putExtra("validityPeriod", validityPeriod);

        if (Rlog.isLoggable("SMS", Log.VERBOSE)) {
            log("Broadcasting sms destAddr: " + destAddr +
                    "\n scAddr=" + scAddr +
                    "\n multipart=" + multipart +
                    "\n callingPackager=" + callingPackage +
                    "\n callingUid=" + android.os.Binder.getCallingUid() +
                    "\n parts=" + parts.size() +
                    "\n sentIntents=" + sentIntents.size() +
                    "\n deliveryIntents=" + deliveryIntents.size() +
                    "\n priority=" + priority +
                    "\n isExpectMore=" + isExpectMore +
                    "\n validityPeriod=" + validityPeriod);
        }
        mContext.sendOrderedBroadcastAsUser(broadcast, UserHandle.OWNER,
                android.Manifest.permission.INTERCEPT_SMS,
                mReceiver, null, Activity.RESULT_OK, destAddr, null);
    }

    @Override
    public void sendText(String callingPackage, String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent)
            throws RemoteException {
        sendTextForSubscriber(getDefaultSmsSubId(), callingPackage, destAddr, scAddr,
                text, sentIntent, deliveryIntent);
    }

    @Override
    public void sendMultipartText(String callingPackage, String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws RemoteException{
        sendMultipartTextForSubscriber(getDefaultSmsSubId(), callingPackage, destAddr,
                scAddr, parts, sentIntents, deliveryIntents);
    }

    @Override
    public List<SmsRawData> getAllMessagesFromIccEfForSubscriber(
            long subId, String callingPkg) throws RemoteException {
        return mIccSmsInterfaceManager.getAllMessagesFromIccEf(callingPkg);
    }

    @Override
    public boolean updateMessageOnIccEfForSubscriber(
            long subId, String callingPkg,
            int messageIndex, int newStatus, byte[] pdu) throws RemoteException {
        return mIccSmsInterfaceManager.updateMessageOnIccEf(callingPkg,
                messageIndex, newStatus, pdu);
    }

    @Override
    public boolean copyMessageToIccEfForSubscriber(
            long subId, String callingPkg, int status,
            byte[] pdu, byte[] smsc) throws RemoteException {
        return mIccSmsInterfaceManager.copyMessageToIccEf(callingPkg, status, pdu, smsc);
    }

    @Override
    public void sendDataForSubscriber(
            long subId, String callingPkg, String destAddr,
            String scAddr, int destPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) throws RemoteException {
        mIccSmsInterfaceManager.sendData(callingPkg, destAddr, scAddr, destPort, data,
                sentIntent, deliveryIntent);
    }

    @Override
    public void sendDataWithOrigPort(
            String callingPkg, String destAddr, String scAddr,
            int destPort, int origPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) throws RemoteException {
        sendDataWithOrigPortUsingSubscriber(getDefaultSmsSubId(), callingPkg, destAddr,
                scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
    }

    @Override
    public void sendDataWithOrigPortUsingSubscriber(
            long subId, String callingPkg, String destAddr, String scAddr,
            int destPort, int origPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) throws RemoteException {
        mIccSmsInterfaceManager.sendDataWithOrigPort(callingPkg, destAddr, scAddr, destPort,
                origPort, data, sentIntent, deliveryIntent);
    }

    @Override
    public void sendTextForSubscriber(
            long subId, String callingPkg, String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        sendTextWithOptionsUsingSubscriber(subId, callingPkg, destAddr, scAddr, text,
                sentIntent, deliveryIntent, -1, false, -1);
    }

    @Override
    public void sendTextWithOptionsUsingSubscriber(
            long subId, String callingPkg, String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent, int priority,
            boolean isExpectMore, int validityPeriod) throws RemoteException {
        mContext.enforceCallingPermission(
                android.Manifest.permission.SEND_SMS,
                "Sending SMS message");
        if (mIccSmsInterfaceManager.isShortSMSCode(destAddr)) {
            mIccSmsInterfaceManager.sendTextWithOptions(callingPkg, destAddr, scAddr, text,
                    sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod);
            return;
        }
        ArrayList<String> parts = new ArrayList<String>();
        parts.add(text);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        sentIntents.add(sentIntent);
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>();
        deliveryIntents.add(deliveryIntent);
        broadcastOutgoingSms(callingPkg, destAddr, scAddr, false, parts, sentIntents,
                deliveryIntents, priority, isExpectMore, validityPeriod);
    }

    @Override
    public void injectSmsPdu(byte[] pdu, String format,
                             PendingIntent receivedIntent) throws RemoteException {
        injectSmsPduForSubscriber(getDefaultSmsSubId(), pdu, format, receivedIntent);
    }

    // FIXME: This needs to be by subscription
    @Override
    public void injectSmsPduForSubscriber(long subId, byte[] pdu, String format,
                                          PendingIntent receivedIntent) throws RemoteException {
        mIccSmsInterfaceManager.injectSmsPdu(pdu, format, receivedIntent);
    }

    @Override
    public void updateSmsSendStatus(int messageRef, boolean success) throws RemoteException {
        mIccSmsInterfaceManager.updateSmsSendStatus(messageRef, success);
    }

    @Override
    public void sendMultipartTextForSubscriber(
            long subId, String callingPkg, String destinationAddress,
            String scAddress, List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws RemoteException {
        sendMultipartTextWithOptionsUsingSubscriber(subId, callingPkg, destinationAddress,
                scAddress, parts, sentIntents, deliveryIntents, -1, false, -1);
    }

    @Override
    public void sendMultipartTextWithOptionsUsingSubscriber(
            long subId, String callingPkg, String destinationAddress, String scAddress,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, int priority, boolean isExpectMore,
            int validityPeriod) throws RemoteException {
        mContext.enforceCallingPermission(
            android.Manifest.permission.SEND_SMS,
            "Sending SMS message");
        if (mIccSmsInterfaceManager.isShortSMSCode(destinationAddress)) {
            mIccSmsInterfaceManager.sendMultipartTextWithOptions(callingPkg, destinationAddress,
                    scAddress, parts, sentIntents, deliveryIntents, -1, false, -1);
            return;
        }
        broadcastOutgoingSms(callingPkg, destinationAddress, scAddress, true,
                parts != null ? new ArrayList<String>(parts) : null,
                sentIntents != null ? new ArrayList<PendingIntent>(sentIntents) : null,
                deliveryIntents != null ? new ArrayList<PendingIntent>(deliveryIntents) : null,
                -1, false, -1);
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

    public boolean enableCellBroadcastRangeForSubscriber(
            long subId, int startMessageId, int endMessageId) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.enableCellBroadcastRange(startMessageId, endMessageId);
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

    public boolean disableCellBroadcastRangeForSubscriber(
            long subId, int startMessageId, int endMessageId) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.disableCellBroadcastRange(startMessageId, endMessageId);
    }

    public int getPremiumSmsPermission(String packageName) {
        return getPremiumSmsPermissionForSubscriber(getDefaultSmsSubId(), packageName);
    }

    @Override
    public int getPremiumSmsPermissionForSubscriber(long subId, String packageName) {
        return mIccSmsInterfaceManager.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        setPremiumSmsPermissionForSubscriber(getDefaultSmsSubId(), packageName, permission);
    }

    @Override
    public void setPremiumSmsPermissionForSubscriber(long subId, String packageName,
                                                     int permission) {
        mIccSmsInterfaceManager.setPremiumSmsPermission(packageName, permission);
    }

    public boolean isImsSmsSupported() {
        return isImsSmsSupportedForSubscriber(getDefaultSmsSubId());
    }

    @Override
    public boolean isImsSmsSupportedForSubscriber(long subId) {
        return mIccSmsInterfaceManager.isImsSmsSupported();
    }

    public String getImsSmsFormat() {
        return getImsSmsFormatForSubscriber(getDefaultSmsSubId());
    }

    @Override
    public String getImsSmsFormatForSubscriber(long subId) {
        return mIccSmsInterfaceManager.getImsSmsFormat();
    }

    @Override
    public void sendStoredText(
            long subId, String callingPkg, Uri messageUri, String scAddress,
            PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        mIccSmsInterfaceManager.sendStoredText(callingPkg, messageUri, scAddress, sentIntent,
                    deliveryIntent);
    }

    @Override
    public void sendStoredMultipartText(long subId, String callingPkg, Uri messageUri,
                                        String scAddress, List<PendingIntent> sentIntents,
                                        List<PendingIntent> deliveryIntents) {
        mIccSmsInterfaceManager.sendStoredMultipartText(callingPkg, messageUri, scAddress,
                sentIntents, deliveryIntents);
    }

    @Override
    public int getSmsCapacityOnIccForSubscriber(long subId) throws RemoteException {
        return mIccSmsInterfaceManager.getSmsCapacityOnIcc();
    }

    protected void log(String msg) {
        Log.d(LOG_TAG, "[IccSmsInterfaceManagerProxy] " + msg);
    }
}
