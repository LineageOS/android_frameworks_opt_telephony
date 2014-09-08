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

import android.content.ContentValues;
import android.os.RemoteException;
import android.os.ServiceManager;
import com.android.internal.telephony.uicc.AdnRecord;


import java.util.List;


/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public class IccPhoneBookInterfaceManagerProxy {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager
            iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public void setmIccPhoneBookInterfaceManager(
            IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(
                efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values,
            String pin2) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsWithContentValuesInEfBySearch(efid,
                values, pin2);
    }

    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid,
                newTag, newPhoneNumber, index, pin2);
    }

    public int[] getAdnRecordsSize(int efid) {
        return mIccPhoneBookInterfaceManager.getAdnRecordsSize(efid);
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        return mIccPhoneBookInterfaceManager.getAdnRecordsInEf(efid);
    }

    public int getAdnCount() {
        return mIccPhoneBookInterfaceManager.getAdnCount();
    }

    public int getAnrCount() {
        return mIccPhoneBookInterfaceManager.getAnrCount();
    }

    public int getEmailCount() {
        return mIccPhoneBookInterfaceManager.getEmailCount();
    }

    public int getSpareAnrCount() {
        return mIccPhoneBookInterfaceManager.getSpareAnrCount();
    }

    public int getSpareEmailCount() {
        return mIccPhoneBookInterfaceManager.getSpareEmailCount();
    }
}
