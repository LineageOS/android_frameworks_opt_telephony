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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsMessage;

public class IccSmsInterfaceManagerProxy extends ISms.Stub {
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
            int priority = intent.getIntExtra("priority", -1);

            if (intent.getIntExtra("callingUid", 0) != 0) {
                callingPackage = callingPackage + "\\" + intent.getIntExtra("callingUid", 0);
            }

            if (intent.getBooleanExtra("multipart", false)) {
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
            if (priority == -1) {
                mIccSmsInterfaceManager.sendText(callingPackage, destAddr, scAddr, text,
                        sentIntent, deliveryIntent);
            }
            else {
                mIccSmsInterfaceManager.sendTextWithPriority(callingPackage, destAddr, scAddr, text,
                        sentIntent, deliveryIntent, priority);
            }
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

    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private static final int WAKE_LOCK_TIMEOUT = 5000;
    private void dispatchPdus(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_RECEIVED_ACTION);
        intent.putExtra("pdus", pdus);
        intent.putExtra("format", SmsMessage.FORMAT_SYNTHETIC);
        dispatch(intent, SMSDispatcher.RECEIVE_SMS_PERMISSION);
    }

    private void dispatch(Intent intent, String permission) {
        // Hold a wake lock for WAKE_LOCK_TIMEOUT seconds, enough to give any
        // receivers time to take their own wake locks.
        mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
        mContext.sendOrderedBroadcast(intent, permission);
    }

    public void synthesizeMessages(String originatingAddress, String scAddress, List<String> messages, long timestampMillis) throws RemoteException {
        mContext.enforceCallingPermission(
                android.Manifest.permission.BROADCAST_SMS, "");
        byte[][] pdus = new byte[messages.size()][];
        for (int i = 0; i < messages.size(); i++) {
            SyntheticSmsMessage message = new SyntheticSmsMessage(originatingAddress, scAddress, messages.get(i), timestampMillis);
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
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mIccSmsInterfaceManager.sendData(callingPackage, destAddr, scAddr, destPort, data,
                sentIntent, deliveryIntent);
    }

    private void broadcastOutgoingSms(String callingPackage, String destAddr, String scAddr,
            boolean multipart, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents,
            int priority) {
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
        mContext.sendOrderedBroadcastAsUser(broadcast, UserHandle.OWNER,
                android.Manifest.permission.INTERCEPT_SMS,
                mReceiver, null, Activity.RESULT_OK, destAddr, null);
    }

    @Override
    public void sendText(String callingPackage, String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mContext.enforceCallingPermission(
                android.Manifest.permission.SEND_SMS,
                "Sending SMS message");
        ArrayList<String> parts = new ArrayList<String>();
        parts.add(text);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        sentIntents.add(sentIntent);
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>();
        deliveryIntents.add(deliveryIntent);
        broadcastOutgoingSms(callingPackage, destAddr, scAddr, false, parts, sentIntents,
                deliveryIntents, -1);
    }

    @Override
    public void sendMultipartText(String callingPackage, String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) {
        mContext.enforceCallingPermission(
                android.Manifest.permission.SEND_SMS,
                "Sending SMS message");
        broadcastOutgoingSms(callingPackage, destAddr, scAddr, true, new ArrayList<String>(parts),
                new ArrayList<PendingIntent>(sentIntents), new ArrayList<PendingIntent>(deliveryIntents),
                -1);
    }

    @Override
    public boolean enableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.enableCellBroadcast(messageIdentifier);
    }

    @Override
    public boolean disableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.disableCellBroadcast(messageIdentifier);
    }

    @Override
    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return mIccSmsInterfaceManager.enableCellBroadcastRange(startMessageId, endMessageId);
    }

    @Override
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return mIccSmsInterfaceManager.disableCellBroadcastRange(startMessageId, endMessageId);
    }

    @Override
    public int getPremiumSmsPermission(String packageName) {
        return mIccSmsInterfaceManager.getPremiumSmsPermission(packageName);
    }

    @Override
    public void setPremiumSmsPermission(String packageName, int permission) {
        mIccSmsInterfaceManager.setPremiumSmsPermission(packageName, permission);
    }

    @Override
    public void sendTextWithPriority(String callingPackage, String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent, int priority)
            throws RemoteException {
        mContext.enforceCallingPermission(
                android.Manifest.permission.SEND_SMS,
                "Sending SMS message");
        ArrayList<String> parts = new ArrayList<String>();
        parts.add(text);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        sentIntents.add(sentIntent);
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>();
        deliveryIntents.add(deliveryIntent);
        broadcastOutgoingSms(callingPackage, destAddr, scAddr, false, parts, sentIntents,
                deliveryIntents, priority);
    }

    @Override
    public boolean isImsSmsSupported() throws RemoteException {
        return mIccSmsInterfaceManager.isImsSmsSupported();
    }

    @Override
    public String getImsSmsFormat() throws RemoteException {
        return mIccSmsInterfaceManager.getImsSmsFormat();
    }
}
