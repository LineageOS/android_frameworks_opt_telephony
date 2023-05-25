/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Messenger;
import android.os.Process;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.TelephonyScanManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for NetworkScanRequestTracker.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NetworkScanRequestTrackerTest extends TelephonyTest {
    private static final String TAG = "NetworkScanRequestTrackerTest";

    private static final String PHONE_PKG = "com.android.phone";
    private static final int PHONE_UID = Process.PHONE_UID;

    private NetworkScanRequestTracker mNetworkScanRequestTracker;
    private HandlerThread mTestHandlerThread;
    private Handler mHandler;
    private int mScanId;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mTestHandlerThread = new HandlerThread(TAG);
        mTestHandlerThread.start();
        mHandler = new Handler(mTestHandlerThread.getLooper());

        mNetworkScanRequestTracker = new NetworkScanRequestTracker();
        mScanId = TelephonyScanManager.INVALID_SCAN_ID;
        assertThat(mHandler).isNotNull();
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        stopNetworkScanIfNeeded(mScanId);
        mTestHandlerThread.quit();
        super.tearDown();
    }

    @Test
    public void testStartNetworkScan_nullRequest_shouldNeverScan() throws Exception {
        NetworkScanRequest request = null;
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when the request is null!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithNullSpecifier_shouldNeverScan() throws Exception {
        RadioAccessSpecifier[] specifiers = null;
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers,
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when no RadioAccessSpecifier is provided!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithEmptySpecifier_shouldNeverScan() throws Exception {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{};
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers,
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when empty RadioAccessSpecifier is "
                        + "provided!")).startNetworkScan(
                any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithTooManySpecifiers_shouldNeverScan()
            throws Exception {
        // More than NetworkScanRequest.MAX_RADIO_ACCESS_NETWORKS (8)
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_T380}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_T410}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_450}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_480}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_710}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_750}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_T810}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_850}, null),
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        new int[]{AccessNetworkConstants.GeranBand.BAND_P900}, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many "
                        + "RadioAccessSpecifiers!")).startNetworkScan(
                any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithTooManyBands_shouldNeverScan() throws Exception {
        // More than NetworkScanRequest.MAX_BANDS (8)
        int[] bands = new int[]{
                AccessNetworkConstants.GeranBand.BAND_T380,
                AccessNetworkConstants.GeranBand.BAND_T410,
                AccessNetworkConstants.GeranBand.BAND_450,
                AccessNetworkConstants.GeranBand.BAND_480,
                AccessNetworkConstants.GeranBand.BAND_710,
                AccessNetworkConstants.GeranBand.BAND_750,
                AccessNetworkConstants.GeranBand.BAND_T810,
                AccessNetworkConstants.GeranBand.BAND_850,
                AccessNetworkConstants.GeranBand.BAND_P900,
        };
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        bands, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many bands!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithTooManyChannels_shouldNeverScan() throws Exception {
        // More than NetworkScanRequest.MAX_CHANNELS (32)
        int[] channels =
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                        22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33,
                };
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, channels),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many channels!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithUnsupportedRan_shouldNeverScan() throws Exception {
        int[] unsupportedRans = new int[]{
                AccessNetworkConstants.AccessNetworkType.UNKNOWN,
                AccessNetworkConstants.AccessNetworkType.CDMA2000,
                AccessNetworkConstants.AccessNetworkType.IWLAN,
        };
        for (int ran : unsupportedRans) {
            RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                    new RadioAccessSpecifier(ran, null, null),
            };
            NetworkScanRequest request = new NetworkScanRequest(
                    NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                    specifiers, /* specifiers */
                    5 /* searchPeriodicity */,
                    60 /* maxSearchTime in seconds */,
                    true /* incrementalResults */,
                    5 /* incrementalResultsPeriodicity */,
                    null /* PLMNs */);
            Messenger messenger = new Messenger(mHandler);

            int scanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                    mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
            processAllMessages();

            verify(mPhone, never().description(
                    "Phone should never start network scan with unsupported RAN "
                            + ran + "!")).startNetworkScan(any(), any());

            // Nothing is needed to clean up on success.
            // This is just for failure cases when startNetworkScan was issued.
            stopNetworkScanIfNeeded(scanId);
        }
    }

    @Test
    public void testStartNetworkScan_requestWithTooSmallPeriodicity_shouldNeverScan()
            throws Exception {
        int searchPeriodicity = NetworkScanRequest.MIN_SEARCH_PERIODICITY_SEC - 1;
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                searchPeriodicity /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too small periodicity!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithTooLargePeriodicity_shouldNeverScan()
            throws Exception {
        int searchPeriodicity = NetworkScanRequest.MAX_SEARCH_MAX_SEC + 1;
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                searchPeriodicity /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan with too large periodicity!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithTooSmallIncPeriodicity_shouldNeverScan()
            throws Exception {
        int incPeriodicity = NetworkScanRequest.MIN_INCREMENTAL_PERIODICITY_SEC - 1;
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                incPeriodicity /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan with too small incremental results "
                        + "periodicity!")).startNetworkScan(
                any(), any());
    }

    @Test
    public void testStartNetworkScan_requestWithTooLargeIncPeriodicity_shouldNeverScan()
            throws Exception {
        int incPeriodicity = NetworkScanRequest.MAX_INCREMENTAL_PERIODICITY_SEC + 1;
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                incPeriodicity /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan with too large incremental periodicity!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_requestPeriodBiggerThanMax_shouldNeverScan() throws Exception {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                61 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when periodicity is larger than max search"
                        + " time!")).startNetworkScan(
                any(), any());
    }

    @Test
    public void testStartNetworkScan_requestIncPeriodBiggerThanMax_shouldNeverScan()
            throws Exception {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                61 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when incremental results periodicity is "
                        + "larger than max search time!")).startNetworkScan(
                any(), any());
    }

    @Test
    public void testStartNetworkScan_requestTooManyPlmns_shouldNeverScan() throws Exception {
        // More than NetworkScanRequest.MAX_MCC_MNC_LIST_SIZE (20) PLMNs
        List<String> plmns = List.of("11110", "11111", "11112", "11113", "11114", "11115", "11116",
                "11117", "11118", "11119", "11120", "11121", "11122", "11123", "11124", "11125",
                "11126", "11127", "11128", "11129", "11130", "11131");

        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN,
                        null, null),
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers, /* specifiers */
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                new ArrayList<>(plmns) /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many PLMNs!"))
                .startNetworkScan(any(), any());
    }

    @Test
    public void testStartNetworkScan_succeed_returnValidScanId() throws Exception {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN, null, null)
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers,
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mPhone).startNetworkScan(any(), any());
        assertThat(mScanId).isNotEqualTo(TelephonyScanManager.INVALID_SCAN_ID);
    }

    @Test
    public void testStartNetworkScan_succeed_deathRecipientIsLinked() throws Exception {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN, null, null)
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers,
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        verify(mIBinder).linkToDeath(any(), anyInt());
    }

    @Test
    public void testStartNetworkScan_multipleRequests_scanIdShouldNotRepeat() throws Exception {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.GERAN, null, null)
        };
        NetworkScanRequest request = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                specifiers,
                5 /* searchPeriodicity */,
                60 /* maxSearchTime in seconds */,
                true /* incrementalResults */,
                5 /* incrementalResultsPeriodicity */,
                null /* PLMNs */);
        Messenger messenger = new Messenger(mHandler);

        int firstScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();
        stopNetworkScanIfNeeded(firstScanId);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, PHONE_UID, -1, PHONE_PKG);
        processAllMessages();

        assertThat(mScanId).isNotEqualTo(firstScanId);
    }

    @Test
    public void testStopNetworkScan_invalidScanId_throwIllegalArgumentException() throws Exception {
        final int invalidScanId = 1000;
        assertThrows(IllegalArgumentException.class,
                () -> mNetworkScanRequestTracker.stopNetworkScan(invalidScanId, PHONE_UID));
    }

    private void stopNetworkScanIfNeeded(int scanId) {
        if (scanId != TelephonyScanManager.INVALID_SCAN_ID) {
            try {
                mNetworkScanRequestTracker.stopNetworkScan(mScanId, PHONE_UID);
                processAllMessages();
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
