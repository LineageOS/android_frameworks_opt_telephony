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

import android.app.PendingIntent;
import android.os.ServiceManager;

import java.util.List;

public class IccSmsInterfaceManagerProxy extends ISms.Stub {
    private IccSmsInterfaceManager mIccSmsInterfaceManager;

    public IccSmsInterfaceManagerProxy(IccSmsInterfaceManager
            iccSmsInterfaceManager) {
        mIccSmsInterfaceManager = iccSmsInterfaceManager;
        if(ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    public void setmIccSmsInterfaceManager(IccSmsInterfaceManager iccSmsInterfaceManager) {
        mIccSmsInterfaceManager = iccSmsInterfaceManager;
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

    @Override
    public void sendText(String callingPackage, String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mIccSmsInterfaceManager.sendText(callingPackage, destAddr, scAddr, text, sentIntent,
                deliveryIntent);
    }

    @Override
    public void sendMultipartText(String callingPackage, String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) {
        mIccSmsInterfaceManager.sendMultipartText(callingPackage, destAddr, scAddr,
                parts, sentIntents, deliveryIntents);
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
}
