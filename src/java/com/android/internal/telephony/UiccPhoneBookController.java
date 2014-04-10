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

import android.content.ContentValues;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.telephony.Rlog;

import com.android.internal.telephony.IccPhoneBookInterfaceManagerProxy;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.AdnRecord;

import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.NullPointerException;
import java.util.List;

public class UiccPhoneBookController extends IIccPhoneBook.Stub {
    private static final String TAG = "UiccPhoneBookController";
    private Phone[] mPhone;

    /* only one UiccPhoneBookController exists */
    public UiccPhoneBookController(Phone[] phone) {
        if (ServiceManager.getService("simphonebook") == null) {
               ServiceManager.addService("simphonebook", this);
        }
        mPhone = phone;
    }

    public boolean
    updateAdnRecordsInEfBySearch (int efid, String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) throws android.os.RemoteException {
        return updateAdnRecordsInEfBySearchForSubscriber(getDefaultSubId(), efid, oldTag,
                oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public boolean
    updateAdnRecordsInEfBySearchForSubscriber(long subId, int efid, String oldTag,
            String oldPhoneNumber, String newTag, String newPhoneNumber,
            String pin2) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfBySearch(efid, oldTag,
                    oldPhoneNumber, newTag, newPhoneNumber, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return false;
        }
    }

    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) throws android.os.RemoteException {
        return updateAdnRecordsInEfByIndexForSubscriber(getDefaultSubId(), efid, newTag,
                newPhoneNumber, index, pin2);
    }

    public boolean
    updateAdnRecordsInEfByIndexForSubscriber(long subId, int efid, String newTag,
            String newPhoneNumber, int index, String pin2) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfByIndex(efid, newTag,
                    newPhoneNumber, index, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsInEfByIndex iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return false;
        }
    }

    public int[] getAdnRecordsSize(int efid) throws android.os.RemoteException {
        return getAdnRecordsSizeForSubscriber(getDefaultSubId(), efid);
    }

    public int[]
    getAdnRecordsSizeForSubscriber(long subId, int efid) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsSize(efid);
        } else {
            Rlog.e(TAG,"getAdnRecordsSize iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) throws android.os.RemoteException {
        return getAdnRecordsInEfForSubscriber(getDefaultSubId(), efid);
    }

    public List<AdnRecord> getAdnRecordsInEfForSubscriber(long subId, int efid)
           throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsInEf(efid);
        } else {
            Rlog.e(TAG,"getAdnRecordsInEf iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return null;
        }
    }

    public boolean
    updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values,
        String pin2) throws android.os.RemoteException {
            return updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(
                getDefaultSubId(), efid, values, pin2);
    }

    public boolean
    updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(long subId, int efid,
        ContentValues values, String pin2)
        throws android.os.RemoteException {

        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsWithContentValuesInEfBySearch(
                efid, values, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsWithContentValuesInEfBySearchUsingSubId " +
                "iccPbkIntMgrProxy is null for Subscription:"+subId);
            return false;
        }
    }

    public int getAdnCount() throws android.os.RemoteException {
        return getAdnCountUsingSubId(getDefaultSubId());
    }

    public int getAdnCountUsingSubId(long subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnCount();
        } else {
            Rlog.e(TAG,"getAdnCount iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return 0;
        }
    }

    public int getAnrCount() throws android.os.RemoteException {
        return getAnrCountUsingSubId(getDefaultSubId());
    }

    public int getAnrCountUsingSubId(long subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAnrCount();
        } else {
            Rlog.e(TAG,"getAnrCount iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return 0;
        }
    }

    public int getEmailCount() throws android.os.RemoteException {
        return getEmailCountUsingSubId(getDefaultSubId());
    }

    public int getEmailCountUsingSubId(long subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getEmailCount();
        } else {
            Rlog.e(TAG,"getEmailCount iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return 0;
        }
    }

    public int getSpareAnrCount() throws android.os.RemoteException {
        return getSpareAnrCountUsingSubId(getDefaultSubId());
    }

    public int getSpareAnrCountUsingSubId(long subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getSpareAnrCount();
        } else {
            Rlog.e(TAG,"getSpareAnrCount iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return 0;
        }
    }

    public int getSpareEmailCount() throws android.os.RemoteException {
        return getSpareEmailCountUsingSubId(getDefaultSubId());
    }

    public int getSpareEmailCountUsingSubId(long subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getSpareEmailCount();
        } else {
            Rlog.e(TAG,"getSpareEmailCount iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return 0;
        }
    }

    /**
     * get phone book interface manager proxy object based on subscription.
     **/
    private IccPhoneBookInterfaceManagerProxy
            getIccPhoneBookInterfaceManagerProxy(long subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        try {
            return ((PhoneProxy)mPhone[phoneId]).getIccPhoneBookInterfaceManagerProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace(); //To print stack trace
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.e(TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace();
            return null;
        }
    }

    private long getDefaultSubId() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }
}
