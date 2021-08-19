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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.PhysicalChannelConfig;
import android.telephony.PhysicalChannelConfig.Builder;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import org.junit.Test;

/** Unit test for {@link android.telephony.PhysicalChannelConfig}. */
public class PhysicalChannelConfigTest {

    private static final int NETWORK_TYPE_NR = TelephonyManager.NETWORK_TYPE_NR;
    private static final int NETWORK_TYPE_LTE = TelephonyManager.NETWORK_TYPE_LTE;
    private static final int NETWORK_TYPE_UMTS = TelephonyManager.NETWORK_TYPE_UMTS;
    private static final int NETWORK_TYPE_GSM = TelephonyManager.NETWORK_TYPE_GSM;
    private static final int CONNECTION_STATUS = PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING;
    private static final int CELL_BANDWIDTH = 12345;
    private static final int FREQUENCY_RANGE = 1;
    private static final int CHANNEL_NUMBER = 1234;
    private static final int CHANNEL_NUMBER_UNKNOWN = PhysicalChannelConfig.CHANNEL_NUMBER_UNKNOWN;
    private static final int[] CONTEXT_IDS = new int[] {123, 555, 1, 0};
    private static final int PHYSICAL_CELL_ID = 502;
    private static final int BAND = 1;
    public static final int INVALID_FREQUENCY = -1;

    private PhysicalChannelConfig mPhysicalChannelConfig;

    private void setUpPhysicalChannelConfig(int networkType, int band, int downlinkChannelNumber,
            int uplinkChannelNumber, int frequencyRange) {
        mPhysicalChannelConfig = new Builder()
                .setCellConnectionStatus(CONNECTION_STATUS)
                .setCellBandwidthDownlinkKhz(CELL_BANDWIDTH)
                .setCellBandwidthUplinkKhz(CELL_BANDWIDTH)
                .setContextIds(CONTEXT_IDS)
                .setPhysicalCellId(PHYSICAL_CELL_ID)
                .setNetworkType(networkType)
                .setFrequencyRange(frequencyRange)
                .setDownlinkChannelNumber(downlinkChannelNumber)
                .setUplinkChannelNumber(uplinkChannelNumber)
                .setBand(band)
                .build();
    }

    @Test
    public void testDownlinkFrequencyForNrArfcn(){
        setUpPhysicalChannelConfig(NETWORK_TYPE_NR, AccessNetworkConstants.NgranBands.BAND_1,
                CHANNEL_NUMBER, CHANNEL_NUMBER, ServiceState.FREQUENCY_RANGE_MID);

        // 3GPP TS 38.104 Table 5.4.2.1-1, {@link AccessNetworkUtils#getFrequencyFromNrArfcn}.
        // Formula of NR-ARFCN convert to actual frequency:
        // Actual frequency(kHz) = (RANGE_OFFSET + GLOBAL_KHZ * (ARFCN - ARFCN_OFFSET))
        assertThat(mPhysicalChannelConfig.getDownlinkFrequencyKhz()).isEqualTo(6170);
    }

    @Test
    public void testDownlinkandUplinkFrequencyForEarfcn(){
        setUpPhysicalChannelConfig(NETWORK_TYPE_LTE, AccessNetworkConstants.EutranBand.BAND_3,
                CHANNEL_NUMBER, 19500, ServiceState.FREQUENCY_RANGE_MID);

        // 3GPP TS 36.101 Table 5.7.3-1, {@link AccessNetworkUtils#getFrequencyFromEarfcn}.
        // Formula of E-UTRA ARFCN convert to actual frequency:
        // Actual frequency(kHz) = (DOWNLINK_LOW + 0.1 * (ARFCN - DOWNLINK_OFFSET)) * FREQUENCY_KHZ
        // Actual frequency(kHz) = (UPLINK_LOW + 0.1 * (ARFCN - UPLINK_OFFSET)) * FREQUENCY_KHZ
        assertThat(mPhysicalChannelConfig.getDownlinkFrequencyKhz()).isEqualTo(1808400);
        assertThat(mPhysicalChannelConfig.getUplinkFrequencyKhz()).isEqualTo(1740000);
    }

    @Test
    public void testDownlinkandUplinkFrequencyForUarfcn(){
        setUpPhysicalChannelConfig(NETWORK_TYPE_UMTS, AccessNetworkConstants.UtranBand.BAND_3,
                CHANNEL_NUMBER, 940, ServiceState.FREQUENCY_RANGE_MID);

        // 3GPP TS 25.101, {@link AccessNetworkUtils#getFrequencyFromUarfcn}.
        // Formula of UTRA ARFCN convert to actual frequency:
        // For general bands:
        // Downlink actual frequency(kHz) = (DOWNLINK_OFFSET + 0.2 * ARFCN) * FREQUENCY_KHZ
        // Uplink actual frequency(kHz) = (UPLINK_OFFSET + 0.2 * ARFCN) * FREQUENCY_KHZ
        assertThat(mPhysicalChannelConfig.getDownlinkFrequencyKhz()).isEqualTo(1821800);
        assertThat(mPhysicalChannelConfig.getUplinkFrequencyKhz()).isEqualTo(1713000);
    }

    @Test
    public void testDownlinkFrequencyForArfcn(){
        setUpPhysicalChannelConfig(NETWORK_TYPE_GSM, AccessNetworkConstants.GeranBand.BAND_450,
                270, 270, ServiceState.FREQUENCY_RANGE_LOW);

        // 3GPP TS 45.005 Table 2-1 Dynamically mapped ARFCN
        // Formula of Geran ARFCN convert to actual frequency:
        // Uplink actual frequency(kHz) =
        //       (UPLINK_FREQUENCY_FIRST + 0.2 * (ARFCN - ARFCN_RANGE_FIRST)) * FREQUENCY_KHZ
        // Downlink actual frequency(kHz) = Uplink actual frequency + 10
        assertThat(mPhysicalChannelConfig.getDownlinkFrequencyKhz()).isEqualTo(452810);
    }

    @Test
    public void testDownlinkandUplinkFrequencyForEarfcnWithIncorrectRange() {
        setUpPhysicalChannelConfig(NETWORK_TYPE_LTE, AccessNetworkConstants.EutranBand.BAND_3,
                900, 900, ServiceState.FREQUENCY_RANGE_MID);

        assertThat(mPhysicalChannelConfig.getDownlinkFrequencyKhz()).isEqualTo(INVALID_FREQUENCY);
    }

    @Test
    public void testFrequencyRangeWithoutBand() {
        try {
            setUpPhysicalChannelConfig(NETWORK_TYPE_UMTS, 0, CHANNEL_NUMBER, CHANNEL_NUMBER,
                    ServiceState.FREQUENCY_RANGE_UNKNOWN);
            fail("Frequency range: 0 is invalid.");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testFrequencyRangeForNrArfcn() {
        setUpPhysicalChannelConfig(NETWORK_TYPE_NR, AccessNetworkConstants.NgranBands.BAND_79,
                4500, 4500, ServiceState.FREQUENCY_RANGE_HIGH);

        assertThat(mPhysicalChannelConfig.getFrequencyRange()).isEqualTo(
                ServiceState.FREQUENCY_RANGE_HIGH);
    }

    @Test
    public void testUplinkFrequencyForNrArfcnWithUnknownChannelNumber(){
        setUpPhysicalChannelConfig(NETWORK_TYPE_NR, AccessNetworkConstants.NgranBands.BAND_1,
                CHANNEL_NUMBER, CHANNEL_NUMBER_UNKNOWN, ServiceState.FREQUENCY_RANGE_MID);

        assertThat(mPhysicalChannelConfig.getUplinkFrequencyKhz()).isEqualTo(INVALID_FREQUENCY);
    }

    @Test
    public void testUplinkFrequencyForNrArfcn(){
        setUpPhysicalChannelConfig(NETWORK_TYPE_NR, AccessNetworkConstants.NgranBands.BAND_1,
                CHANNEL_NUMBER, CHANNEL_NUMBER, ServiceState.FREQUENCY_RANGE_MID);

        // 3GPP TS 38.104 Table 5.4.2.1-1, {@link AccessNetworkUtils#getFrequencyFromNrArfcn}.
        // Formula of NR-ARFCN convert to actual frequency:
        // Actual frequency(kHz) = (RANGE_OFFSET + GLOBAL_KHZ * (ARFCN - ARFCN_OFFSET))
        assertThat(mPhysicalChannelConfig.getUplinkFrequencyKhz()).isEqualTo(6170);
    }

    @Test
    public void testBuilder() {
        setUpPhysicalChannelConfig(NETWORK_TYPE_LTE, BAND, CHANNEL_NUMBER, CHANNEL_NUMBER,
                FREQUENCY_RANGE);

        assertThat(mPhysicalChannelConfig.getNetworkType()).isEqualTo(NETWORK_TYPE_LTE);
        assertThat(mPhysicalChannelConfig.getConnectionStatus()).isEqualTo(CONNECTION_STATUS);
        assertThat(mPhysicalChannelConfig.getCellBandwidthDownlinkKhz()).isEqualTo(CELL_BANDWIDTH);
        assertThat(mPhysicalChannelConfig.getCellBandwidthUplinkKhz()).isEqualTo(CELL_BANDWIDTH);
        assertThat(mPhysicalChannelConfig.getFrequencyRange()).isEqualTo(FREQUENCY_RANGE);
        assertThat(mPhysicalChannelConfig.getContextIds()).isEqualTo(CONTEXT_IDS);
        assertThat(mPhysicalChannelConfig.getPhysicalCellId()).isEqualTo(PHYSICAL_CELL_ID);
        assertThat(mPhysicalChannelConfig.getDownlinkChannelNumber()).isEqualTo(CHANNEL_NUMBER);
        assertThat(mPhysicalChannelConfig.getUplinkChannelNumber()).isEqualTo(CHANNEL_NUMBER);
    }

    @Test
    public void testParcel() {
        setUpPhysicalChannelConfig(NETWORK_TYPE_LTE, BAND, CHANNEL_NUMBER, CHANNEL_NUMBER,
                ServiceState.FREQUENCY_RANGE_MID);

        Parcel parcel = Parcel.obtain();
        mPhysicalChannelConfig.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);

        PhysicalChannelConfig fromParcel = PhysicalChannelConfig.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel).isEqualTo(mPhysicalChannelConfig);
    }
}
