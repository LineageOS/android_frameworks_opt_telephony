/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.telephony.uicc.AdnRecord;

import org.junit.Test;

import java.util.ArrayList;

public class FdnUtilsTest {

    private ArrayList<AdnRecord> initializeFdnList() {
        ArrayList<AdnRecord> fdnList = new ArrayList<>();
        AdnRecord adnRecord = new AdnRecord(null, null);
        // By default, every sim card holds 15 empty FDN records
        int fdnListSize = 15;
        for (int i = 0; i < fdnListSize; i++) {
            fdnList.add(adnRecord);
        }
        return fdnList;
    }

    @Test
    public void fdnListIsNull_returnsFalse() {
        assertFalse(FdnUtils.isFDN( "123456789", "US", null));
    }

    @Test
    public void fdnListIsEmpty_returnsFalse() {
        ArrayList<AdnRecord> fdnList = new ArrayList<>();
        assertFalse(FdnUtils.isFDN( "123456789", "US", fdnList));
    }

    @Test
    public void fdnListHasOnlyDefaultRecords_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();

        assertFalse(FdnUtils.isFDN( "123456789", "US", fdnList));
    }

    @Test
    public void fdnListHasRecordWithEmptyNumberStr_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "");
        fdnList.add(1, adnRecord);

        assertFalse(FdnUtils.isFDN( "123456789", "US", fdnList));
    }

    @Test
    public void dialStrInFdnList_returnsTrue() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "123456789");
        fdnList.add(2, adnRecord);

        assertTrue(FdnUtils.isFDN( "123456789", "US", fdnList));
    }

    @Test
    public void dialStrNotInFdnList_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "111111111");
        fdnList.add(3, adnRecord);

        assertFalse(FdnUtils.isFDN("123456788", "US", fdnList));
    }

    @Test
    public void dialStrIsNull_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "111111111");
        fdnList.add(4, adnRecord);

        assertFalse(FdnUtils.isFDN( null, "US", fdnList));
    }

    @Test
    public void fdnEntryFirstSubStringOfDialStr_returnsTrue() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "123");
        fdnList.add(5, adnRecord);

        assertTrue(FdnUtils.isFDN( "12345", "US", fdnList));
    }

    @Test
    public void fdnEntrySubStringOfDialStr_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "123");
        fdnList.add(5, adnRecord);

        assertFalse(FdnUtils.isFDN("612345", "US", fdnList));
    }

    @Test
    public void dialStrFirstSubStringOfFdnEntry_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "12345");
        fdnList.add(5, adnRecord);

        assertFalse(FdnUtils.isFDN("123", "US", fdnList));
    }

    @Test
    public void dialStrSubStringOfFdnEntry_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "612345");
        fdnList.add(5, adnRecord);

        assertFalse(FdnUtils.isFDN("123", "US", fdnList));
    }

    @Test
    public void dialStrWithoutCountryCode_returnsTrue() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "+16502910000");
        fdnList.add(6, adnRecord);

        assertTrue(FdnUtils.isFDN( "6502910000", "US", fdnList));
    }

    @Test
    public void dialStrWithCountryCode_returnsTrue() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "6502910000");
        fdnList.add(6, adnRecord);

        assertTrue(FdnUtils.isFDN("+16502910000", "US", fdnList));
    }

    @Test
    public void defaultCountryIsoIsEmpty_returnsTrue() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "650");
        fdnList.add(6, adnRecord);

        assertTrue(FdnUtils.isFDN("+16502910000", "", fdnList));
    }

    @Test
    public void defaultCountryIsoIsEmpty_returnsFalse() {
        ArrayList<AdnRecord> fdnList = initializeFdnList();
        AdnRecord adnRecord = new AdnRecord(null, "+1650");
        fdnList.add(6, adnRecord);

        assertFalse(FdnUtils.isFDN("6502910000", "", fdnList));
    }
}