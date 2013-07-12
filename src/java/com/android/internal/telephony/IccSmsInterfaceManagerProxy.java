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
            ArrayList<String> parts = intent.getStringArrayListExtra("parts");
            ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");
            ArrayList<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents");

            if (intent.getBooleanExtra("multipart", false)) {
                mIccSmsInterfaceManager.sendMultipartText(destAddr, scAddr,
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
            mIccSmsInterfaceManager.sendText(destAddr, scAddr, text,
                    sentIntent, deliveryIntent);
        }
    };

    public IccSmsInterfaceManagerProxy(Context context,
            IccSmsInterfaceManager iccSmsInterfaceManager) {
        this.mContext = context;
        this.mIccSmsInterfaceManager = iccSmsInterfaceManager;
        if(ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }

        createWakelock();
    }

    public void setmIccSmsInterfaceManager(IccSmsInterfaceManager iccSmsInterfaceManager) {
        this.mIccSmsInterfaceManager = iccSmsInterfaceManager;
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

    @Override
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

    public boolean
    updateMessageOnIccEf(int index, int status, byte[] pdu) throws android.os.RemoteException {
         return mIccSmsInterfaceManager.updateMessageOnIccEf(index, status, pdu);
    }

    public boolean copyMessageToIccEf(int status, byte[] pdu,
            byte[] smsc) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.copyMessageToIccEf(status, pdu, smsc);
    }

    public List<SmsRawData> getAllMessagesFromIccEf() throws android.os.RemoteException {
        return mIccSmsInterfaceManager.getAllMessagesFromIccEf();
    }

    public void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mIccSmsInterfaceManager.sendData(destAddr, scAddr, destPort, data,
                sentIntent, deliveryIntent);
    }

    private void broadcastOutgoingSms(String destAddr, String scAddr,
            boolean multipart, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        Intent broadcast = new Intent(Intent.ACTION_NEW_OUTGOING_SMS);
        broadcast.putExtra("destAddr", destAddr);
        broadcast.putExtra("scAddr", scAddr);
        broadcast.putExtra("multipart", multipart);
        broadcast.putStringArrayListExtra("parts", parts);
        broadcast.putParcelableArrayListExtra("sentIntents", sentIntents);
        broadcast.putParcelableArrayListExtra("deliveryIntents", deliveryIntents);
        mContext.sendOrderedBroadcastAsUser(broadcast, UserHandle.OWNER,
                android.Manifest.permission.INTERCEPT_SMS,
                mReceiver, null, Activity.RESULT_OK, destAddr, null);
    }

    public void sendText(String destAddr, String scAddr,
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
        broadcastOutgoingSms(destAddr, scAddr, false, parts, sentIntents, deliveryIntents);
    }

    public void sendMultipartText(String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
        mContext.enforceCallingPermission(
                android.Manifest.permission.SEND_SMS,
                "Sending SMS message");
        broadcastOutgoingSms(destAddr, scAddr, true, new ArrayList<String>(parts),
                new ArrayList<PendingIntent>(sentIntents), new ArrayList<PendingIntent>(deliveryIntents));
    }

    public boolean enableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.enableCellBroadcast(messageIdentifier);
    }

    public boolean disableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.disableCellBroadcast(messageIdentifier);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return mIccSmsInterfaceManager.enableCellBroadcastRange(startMessageId, endMessageId);
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return mIccSmsInterfaceManager.disableCellBroadcastRange(startMessageId, endMessageId);
    }

    public int getPremiumSmsPermission(String packageName) {
        return mIccSmsInterfaceManager.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        mIccSmsInterfaceManager.setPremiumSmsPermission(packageName, permission);
    }
}
