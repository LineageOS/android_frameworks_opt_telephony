/*
 * Copyright (C) 2006 The Android Open Source Project
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
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.test.suitebuilder.annotation.Suppress;

import com.android.internal.telephony.IccProvider;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;

import junit.framework.TestCase;

import java.util.List;

@Suppress
public class SimPhoneBookTest extends TestCase {

    public void testBasic() throws Exception {
        IIccPhoneBook simPhoneBook =
                IIccPhoneBook.Stub.asInterface(
                        TelephonyFrameworkInitializer
                                .getTelephonyServiceManager()
                                .getIccPhoneBookServiceRegisterer()
                                .get());
        assertNotNull(simPhoneBook);

        final int subId = SubscriptionManager.getDefaultSubscriptionId();
        int size[] = simPhoneBook.getAdnRecordsSizeForSubscriber(subId, IccConstants.EF_ADN);
        assertNotNull(size);
        assertEquals(3, size.length);
        assertEquals(size[0] * size[2], size[1]);
        assertTrue(size[2] >= 100);

        List<AdnRecord> adnRecordList =
                simPhoneBook.getAdnRecordsInEfForSubscriber(subId, IccConstants.EF_ADN);
        // do it twice cause the second time shall read from cache only
        adnRecordList = simPhoneBook.getAdnRecordsInEfForSubscriber(subId, IccConstants.EF_ADN);
        assertNotNull(adnRecordList);

        // Test for phone book update
        int adnIndex, listIndex = 0;
        AdnRecord originalAdn = null;
        // We need to maintain the state of the SIM before and after the test.
        // Since this test doesn't mock the SIM we try to get a valid ADN record,
        // for 3 tries and if this fails, we bail out.
        for (adnIndex = 3 ; adnIndex >= 1; adnIndex--) {
            listIndex = adnIndex - 1; // listIndex is zero based.
            originalAdn = adnRecordList.get(listIndex);
            assertNotNull("Original Adn is Null.", originalAdn);
            assertNotNull("Original Adn alpha tag is null.", originalAdn.getAlphaTag());
            assertNotNull("Original Adn number is null.", originalAdn.getNumber());
            
            if (originalAdn.getNumber().length() > 0 &&  
                originalAdn.getAlphaTag().length() > 0) {   
                break;
            }
        }
        if (adnIndex == 0) return;
        
        AdnRecord emptyAdn = new AdnRecord("", "");
        AdnRecord firstAdn = new AdnRecord("John", "4085550101");
        AdnRecord secondAdn = new AdnRecord("Andy", "6505550102");
        String pin2 = null;

        // udpate by index
        ContentValues values = new ContentValues();
        values.put(IccProvider.STR_NEW_TAG, firstAdn.getAlphaTag());
        values.put(IccProvider.STR_NEW_NUMBER, firstAdn.getNumber());
        values.put(IccProvider.STR_NEW_EMAILS, "");
        values.put(IccProvider.STR_NEW_ANRS, "");
        boolean success = simPhoneBook.updateAdnRecordsInEfByIndexForSubscriber(subId,
                IccConstants.EF_ADN, values, adnIndex, pin2);
        adnRecordList =
                simPhoneBook.getAdnRecordsInEfForSubscriber(subId, IccConstants.EF_ADN);
        AdnRecord tmpAdn = adnRecordList.get(listIndex);
        assertTrue(success);
        assertTrue(firstAdn.isEqual(tmpAdn));

        // replace by search
        ContentValues values2 = new ContentValues();
        values.put(IccProvider.STR_TAG, firstAdn.getAlphaTag());
        values.put(IccProvider.STR_NUMBER, firstAdn.getNumber());
        values.put(IccProvider.STR_EMAILS, "");
        values.put(IccProvider.STR_ANRS, "");
        values.put(IccProvider.STR_NEW_TAG, secondAdn.getAlphaTag());
        values.put(IccProvider.STR_NEW_NUMBER, secondAdn.getNumber());
        values.put(IccProvider.STR_NEW_EMAILS, "");
        values.put(IccProvider.STR_NEW_ANRS, "");
        success = simPhoneBook.updateAdnRecordsInEfBySearchForSubscriber(subId,
                IccConstants.EF_ADN, values2, pin2);
        adnRecordList =
                simPhoneBook.getAdnRecordsInEfForSubscriber(subId, IccConstants.EF_ADN);
        tmpAdn = adnRecordList.get(listIndex);
        assertTrue(success);
        assertFalse(firstAdn.isEqual(tmpAdn));
        assertTrue(secondAdn.isEqual(tmpAdn));

        // erase be search
        ContentValues values3 = new ContentValues();
        values.put(IccProvider.STR_TAG, secondAdn.getAlphaTag());
        values.put(IccProvider.STR_NUMBER, secondAdn.getNumber());
        values.put(IccProvider.STR_EMAILS, "");
        values.put(IccProvider.STR_ANRS, "");
        values.put(IccProvider.STR_NEW_TAG, emptyAdn.getAlphaTag());
        values.put(IccProvider.STR_NEW_NUMBER, emptyAdn.getNumber());
        values.put(IccProvider.STR_NEW_EMAILS, "");
        values.put(IccProvider.STR_NEW_ANRS, "");
        success = simPhoneBook.updateAdnRecordsInEfBySearchForSubscriber(subId,
                IccConstants.EF_ADN, values3, pin2);
        adnRecordList =
                simPhoneBook.getAdnRecordsInEfForSubscriber(subId, IccConstants.EF_ADN);
        tmpAdn = adnRecordList.get(listIndex);
        assertTrue(success);
        assertTrue(tmpAdn.isEmpty());

        // restore the orginial adn
        ContentValues values4 = new ContentValues();
        values.put(IccProvider.STR_NEW_TAG, originalAdn.getAlphaTag());
        values.put(IccProvider.STR_NEW_NUMBER, originalAdn.getNumber());
        values.put(IccProvider.STR_NEW_EMAILS, "");
        values.put(IccProvider.STR_NEW_ANRS, "");
        success = simPhoneBook.updateAdnRecordsInEfByIndexForSubscriber(subId,
                IccConstants.EF_ADN, values4, adnIndex, pin2);
        adnRecordList =
                simPhoneBook.getAdnRecordsInEfForSubscriber(subId, IccConstants.EF_ADN);
        tmpAdn = adnRecordList.get(listIndex);
        assertTrue(success);
        assertTrue(originalAdn.isEqual(tmpAdn));
    }
}
