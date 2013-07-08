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
import java.util.Hashtable;
import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsMessage;
import android.util.Log;

public class IccSmsInterfaceManagerProxy extends ISms.Stub {
	private static final String INTERCEPT_SMS_PERMISSION = "android.permission.INTERCEPT_SMS";

    private IccSmsInterfaceManager mIccSmsInterfaceManager;

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			try {
				if (getResultCode() != Activity.RESULT_OK)
					return;
				boolean multipart = intent.getBooleanExtra("multipart", false);
				if (!multipart) {
					String destAddr = intent.getStringExtra("destAddr");
					String scAddr = intent.getStringExtra("scAddr");
					String text = intent.getStringExtra("text");
					PendingIntent sentIntent = intent
							.getParcelableExtra("sentIntent");
					PendingIntent deliveryIntent = intent
							.getParcelableExtra("deliveryIntent");
					mIccSmsInterfaceManager.sendText(destAddr, scAddr, text,
							sentIntent, deliveryIntent);
				} else {
					String destAddr = intent.getStringExtra("destAddr");
					String scAddr = intent.getStringExtra("scAddr");
					ArrayList<String> parts = intent
							.getStringArrayListExtra("parts");
					ArrayList<PendingIntent> sentIntents = intent
							.getParcelableArrayListExtra("sentIntent");
					ArrayList<PendingIntent> deliveryIntents = intent
							.getParcelableArrayListExtra("deliveryIntents");
					mIccSmsInterfaceManager.sendMultipartText(destAddr, scAddr,
							parts, sentIntents, deliveryIntents);
				}
			} catch (Exception e) {
				Log.i(getClass().getSimpleName(), "Error during sending SMS", e);
			}
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
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSDispatcher");
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
                "android.permission.BROADCAST_SMS", "");
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

    public void sendText(String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mContext.enforceCallingPermission(
                "android.permission.SEND_SMS",
                "Sending SMS message");
		Intent broadcast = new Intent(Intent.ACTION_NEW_OUTGOING_SMS);
		broadcast.putExtra("multipart", false);
		if (destAddr != null)
			broadcast.putExtra("destAddr", destAddr);
		if (scAddr != null)
			broadcast.putExtra("scAddr", scAddr);
		if (text != null)
			broadcast.putExtra("text", text);
		if (sentIntent != null)
			broadcast.putExtra("sentIntent", sentIntent);
		if (deliveryIntent != null)
			broadcast.putExtra("deliveryIntent", deliveryIntent);
		mContext.sendOrderedBroadcast(broadcast, INTERCEPT_SMS_PERMISSION,
				mReceiver, null, Activity.RESULT_OK, null, null);
    }

    public void sendMultipartText(String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
        mContext.enforceCallingPermission(
                "android.permission.SEND_SMS",
                "Sending SMS message");
		Intent broadcast = new Intent(Intent.ACTION_NEW_OUTGOING_SMS);
		broadcast.putExtra("multipart", false);
		if (destAddr != null)
			broadcast.putExtra("destAddr", destAddr);
		if (scAddr != null)
			broadcast.putExtra("scAddr", scAddr);
		if (parts != null)
			broadcast.putStringArrayListExtra("parts", new ArrayList<String>(
					parts));
		if (sentIntents != null)
			broadcast.putParcelableArrayListExtra("sentIntents",
					new ArrayList<PendingIntent>(sentIntents));
		if (deliveryIntents != null)
			broadcast.putParcelableArrayListExtra("deliveryIntents",
					new ArrayList<PendingIntent>(deliveryIntents));
		mContext.sendOrderedBroadcast(broadcast, INTERCEPT_SMS_PERMISSION,
				mReceiver, null, Activity.RESULT_OK, null, null);
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
