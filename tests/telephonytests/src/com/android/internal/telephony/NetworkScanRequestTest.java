/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.telephony.AccessNetworkConstants.EutranBand;
import android.telephony.AccessNetworkConstants.GeranBand;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link NetworkScanRequest}. */
public class NetworkScanRequestTest {

    @Test
    @SmallTest
    public void testParcel() {
        NetworkScanRequest nsq = createNetworkScanRequest();

        Parcel p = Parcel.obtain();
        nsq.writeToParcel(p, 0);
        p.setDataPosition(0);

        NetworkScanRequest newNsq = NetworkScanRequest.CREATOR.createFromParcel(p);
        assertEquals(nsq, newNsq);
    }

    @Test
    @SmallTest
    public void testEquals_identity_allFieldsNonNull() {
        NetworkScanRequest nsq = createNetworkScanRequest();

        assertEquals(nsq, nsq);
    }

    @Test
    @SmallTest
    public void testEquals_identify_nullRadioAccessSpecifiers() {
        NetworkScanRequest nsq = createNetworkScanRequest(null, List.of("310480"));

        assertEquals(nsq, nsq);
    }

    @Test
    @SmallTest
    public void testEquals_identify_emptyRadioAccessSpecifiers() {
        NetworkScanRequest nsq = createNetworkScanRequest(new RadioAccessSpecifier[]{},
                List.of("310480"));

        assertEquals(nsq, nsq);
    }

    @Test
    @SmallTest
    public void testEquals_identify_nullPlmns() {
        NetworkScanRequest nsq = createNetworkScanRequest(new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkType.GERAN, null, null)}, null);

        assertEquals(nsq, nsq);
    }

    @Test
    @SmallTest
    public void testEquals_identify_emptyPlmns() {
        NetworkScanRequest nsq = createNetworkScanRequest(new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkType.GERAN, null, null)}, List.of());

        assertEquals(nsq, nsq);
    }

    @Test
    @SmallTest
    public void testEquals_identify_nullRasAndPlmns() {
        NetworkScanRequest nsq = createNetworkScanRequest(null, null);

        assertEquals(nsq, nsq);
    }

    @Test
    @SmallTest
    public void testEquals_sameValues_allFieldsNonNull() {
        NetworkScanRequest nsq1 = createNetworkScanRequest();
        NetworkScanRequest nsq2 = createNetworkScanRequest();

        assertEquals(nsq1, nsq2);
    }

    @Test
    @SmallTest
    public void testEquals_sameValues_nullRadioAccessSpecifiers() {
        NetworkScanRequest nsq1 = createNetworkScanRequest(null, List.of("310480"));
        NetworkScanRequest nsq2 = createNetworkScanRequest(null, List.of("310480"));

        assertEquals(nsq1, nsq2);
    }

    @Test
    @SmallTest
    public void testEquals_sameValues_emptyRadioAccessSpecifiers() {
        NetworkScanRequest nsq1 = createNetworkScanRequest(new RadioAccessSpecifier[]{},
                List.of("310480"));
        NetworkScanRequest nsq2 = createNetworkScanRequest(new RadioAccessSpecifier[]{},
                List.of("310480"));

        assertEquals(nsq1, nsq2);
    }

    @Test
    @SmallTest
    public void testEquals_sameValues_nullPlmns() {
        NetworkScanRequest nsq1 = createNetworkScanRequest(new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkType.GERAN, null, null)}, null);
        NetworkScanRequest nsq2 = createNetworkScanRequest(new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkType.GERAN, null, null)}, null);

        assertEquals(nsq1, nsq2);
    }

    @Test
    @SmallTest
    public void testEquals_sameValues_emptyPlmns() {
        NetworkScanRequest nsq1 = createNetworkScanRequest(new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkType.GERAN, null, null)}, List.of());
        NetworkScanRequest nsq2 = createNetworkScanRequest(new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkType.GERAN, null, null)}, List.of());

        assertEquals(nsq1, nsq2);
    }

    @Test
    @SmallTest
    public void testEquals_sameValues_nullRasAndPlmns() {
        NetworkScanRequest nsq1 = createNetworkScanRequest(null, null);
        NetworkScanRequest nsq2 = createNetworkScanRequest(null, null);

        assertEquals(nsq1, nsq2);
    }

    @Test
    @SmallTest
    public void testEquals_plmnsInDifferentOrder_shouldNotEqual() {
        NetworkScanRequest nsq1 = createNetworkScanRequest(null, List.of("123456", "987654"));
        NetworkScanRequest nsq2 = createNetworkScanRequest(null, List.of("987654", "123456"));

        assertNotEquals(nsq1, nsq2);
    }

    private NetworkScanRequest createNetworkScanRequest() {
        int ranGsm = AccessNetworkType.GERAN;
        int[] gsmBands = {GeranBand.BAND_T380, GeranBand.BAND_T410};
        int[] gsmChannels = {1, 2, 3, 4};
        RadioAccessSpecifier gsm = new RadioAccessSpecifier(ranGsm, gsmBands, gsmChannels);
        int ranLte = AccessNetworkType.EUTRAN;
        int[] lteBands = {EutranBand.BAND_10, EutranBand.BAND_11};
        int[] lteChannels = {5, 6, 7, 8};
        RadioAccessSpecifier lte = new RadioAccessSpecifier(ranLte, lteBands, lteChannels);
        RadioAccessSpecifier[] ras = {gsm, lte};

        int searchPeriodicity = 70;
        int maxSearchTime = 200;
        boolean incrementalResults = true;
        int incrementalResultsPeriodicity = 7;

        ArrayList<String> mccmncs = new ArrayList<String>();
        mccmncs.add("310480");
        mccmncs.add("21002");

        return new NetworkScanRequest(NetworkScanRequest.SCAN_TYPE_ONE_SHOT, ras,
                searchPeriodicity, maxSearchTime, incrementalResults,
                incrementalResultsPeriodicity, mccmncs);
    }

    private NetworkScanRequest createNetworkScanRequest(
            RadioAccessSpecifier[] radioAccessSpecifiers, List<String> plmns) {
        int searchPeriodicity = 70;
        int maxSearchTime = 200;
        boolean incrementalResults = true;
        int incrementalResultsPeriodicity = 7;

        return new NetworkScanRequest(NetworkScanRequest.SCAN_TYPE_ONE_SHOT, radioAccessSpecifiers,
                searchPeriodicity, maxSearchTime, incrementalResults,
                incrementalResultsPeriodicity, plmns != null ? new ArrayList<>(plmns) : null);
    }
}
