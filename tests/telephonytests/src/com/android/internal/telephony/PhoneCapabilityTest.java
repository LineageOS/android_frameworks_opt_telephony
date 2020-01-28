/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.PhoneCapability;
import android.telephony.SimSlotCapability;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PhoneCapabilityTest {
    @Test
    public void basicTests() {
        int utranUeCategoryDl = 1;
        int utranUeCategoryUl = 2;
        int eutranUeCategoryDl = 3;
        int eutranUeCategoryUl = 4;
        long lingerTime = 5;
        long supportedRats = 6;
        List<Integer> geranBands = new ArrayList<>();
        geranBands.add(1);
        List<Integer> utranBands = new ArrayList<>();
        utranBands.add(2);
        List<Integer> eutranBands = new ArrayList<>();
        eutranBands.add(3);
        List<Integer> ngranBands = new ArrayList<>();
        ngranBands.add(4);
        List<String> logicalModemUuids = new ArrayList<>();
        logicalModemUuids.add("com.google.android.lm0");
        List<SimSlotCapability> simSlotCapabilities = new ArrayList<>();
        simSlotCapabilities.add(new SimSlotCapability(1, 2));
        List<List<Long>> concurrentFeaturesSupport = new ArrayList<>();
        List<Long> feature = new ArrayList<>();
        feature.add(PhoneCapability.MODEM_FEATURE_CDMA2000_EHRPD_REG);
        concurrentFeaturesSupport.add(feature);

        PhoneCapability capability = new PhoneCapability(utranUeCategoryDl, utranUeCategoryUl,
                eutranUeCategoryDl, eutranUeCategoryUl, lingerTime,
                supportedRats, geranBands, utranBands, eutranBands, ngranBands, logicalModemUuids,
                simSlotCapabilities, concurrentFeaturesSupport);

        assertEquals(utranUeCategoryDl, capability.getUeCategory(false, AccessNetworkType.UTRAN));
        assertEquals(utranUeCategoryUl, capability.getUeCategory(true, AccessNetworkType.UTRAN));
        assertEquals(eutranUeCategoryDl, capability.getUeCategory(false, AccessNetworkType.EUTRAN));
        assertEquals(eutranUeCategoryUl, capability.getUeCategory(true, AccessNetworkType.EUTRAN));
        assertEquals(lingerTime, capability.getPsDataConnectionLingerTimeMillis());
        assertEquals(supportedRats, capability.getSupportedRats());
        assertEquals(geranBands, capability.getBands(AccessNetworkType.GERAN));
        assertEquals(utranBands, capability.getBands(AccessNetworkType.UTRAN));
        assertEquals(eutranBands, capability.getBands(AccessNetworkType.EUTRAN));
        assertEquals(ngranBands, capability.getBands(AccessNetworkType.NGRAN));
        assertEquals(logicalModemUuids, capability.getLogicalModemUuids());
        assertEquals(simSlotCapabilities, capability.getSimSlotCapabilities());
        assertEquals(concurrentFeaturesSupport, capability.getConcurrentFeaturesSupport());

        PhoneCapability toCompare = new PhoneCapability(utranUeCategoryDl + 1,
                utranUeCategoryUl + 1, eutranUeCategoryDl + 1, eutranUeCategoryUl + 1,
                lingerTime + 1, supportedRats + 1, geranBands, utranBands, eutranBands, ngranBands,
                logicalModemUuids, simSlotCapabilities, concurrentFeaturesSupport);
        assertEquals(capability, new PhoneCapability(utranUeCategoryDl, utranUeCategoryUl,
                eutranUeCategoryDl, eutranUeCategoryUl, lingerTime,
                supportedRats, geranBands, utranBands, eutranBands, ngranBands, logicalModemUuids,
                simSlotCapabilities, concurrentFeaturesSupport));
        assertNotEquals(capability, toCompare);
    }

    @Test
    public void parcelReadWrite() {
        int utranUeCategoryDl = 1;
        int utranUeCategoryUl = 2;
        int eutranUeCategoryDl = 3;
        int eutranUeCategoryUl = 4;
        long lingerTime = 5;
        long supportedRats = 6;
        List<Integer> geranBands = new ArrayList<>();
        geranBands.add(1);
        List<Integer> utranBands = new ArrayList<>();
        utranBands.add(2);
        List<Integer> eutranBands = new ArrayList<>();
        eutranBands.add(3);
        List<Integer> ngranBands = new ArrayList<>();
        ngranBands.add(4);
        List<String> logicalModemUuids = new ArrayList<>();
        logicalModemUuids.add("com.google.android.lm0");
        List<SimSlotCapability> simSlotCapabilities = new ArrayList<>();
        simSlotCapabilities.add(new SimSlotCapability(1, 2));
        List<List<Long>> concurrentFeaturesSupport = new ArrayList<>();
        List<Long> feature = new ArrayList<>();
        feature.add(PhoneCapability.MODEM_FEATURE_NETWORK_SCAN);
        concurrentFeaturesSupport.add(feature);

        PhoneCapability capability = new PhoneCapability(utranUeCategoryDl, utranUeCategoryUl,
                eutranUeCategoryDl, eutranUeCategoryUl, lingerTime,
                supportedRats, geranBands, utranBands, eutranBands, ngranBands, logicalModemUuids,
                simSlotCapabilities, concurrentFeaturesSupport);

        Parcel parcel = Parcel.obtain();
        capability.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PhoneCapability toCompare = PhoneCapability.CREATOR.createFromParcel(parcel);

        assertEquals(utranUeCategoryDl, capability.getUeCategory(false, AccessNetworkType.UTRAN));
        assertEquals(utranUeCategoryUl, capability.getUeCategory(true, AccessNetworkType.UTRAN));
        assertEquals(eutranUeCategoryDl, capability.getUeCategory(false, AccessNetworkType.EUTRAN));
        assertEquals(eutranUeCategoryUl, capability.getUeCategory(true, AccessNetworkType.EUTRAN));
        assertEquals(lingerTime, capability.getPsDataConnectionLingerTimeMillis());
        assertEquals(supportedRats, capability.getSupportedRats());
        assertEquals(geranBands, capability.getBands(AccessNetworkType.GERAN));
        assertEquals(utranBands, capability.getBands(AccessNetworkType.UTRAN));
        assertEquals(eutranBands, capability.getBands(AccessNetworkType.EUTRAN));
        assertEquals(ngranBands, capability.getBands(AccessNetworkType.NGRAN));
        assertEquals(logicalModemUuids, capability.getLogicalModemUuids());
        assertEquals(simSlotCapabilities, capability.getSimSlotCapabilities());
        assertEquals(concurrentFeaturesSupport, capability.getConcurrentFeaturesSupport());
        assertEquals(capability, toCompare);
    }
}
