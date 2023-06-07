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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.UserHandle;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellInfoLte;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.TelephonyScanManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.internal.telephony.NetworkScanRequestTracker.NetworkScanRequestInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for NetworkScanRequestTracker.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NetworkScanRequestTrackerTest extends TelephonyTest {
    private static final String TAG = "NetworkScanRequestTrackerTest";

    private static final String CLIENT_PKG = "com.android.testapp";
    private static final int CLIENT_UID = 123456;

    // Keep the same as in NetworkScanRequestTracker.
    // This is the only internal implementation that the UT has to depend on
    // in order to fully emulate NetworkScanResult in various cases
    private static final int EVENT_RECEIVE_NETWORK_SCAN_RESULT = 3;

    // Mocks
    private CommandsInterface mMockCi;

    private NetworkScanRequestTracker mNetworkScanRequestTracker;
    private HandlerThread mTestHandlerThread;
    private Handler mHandler;
    private int mScanId;
    private List<Message> mMessages = new ArrayList<>();
    // Latch to make sure all messages are received before verifying.
    // Default count is 1 but can override to match expected msg number
    private CountDownLatch mMessageLatch = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mMockCi = Mockito.mock(CommandsInterface.class);
        mPhone.mCi = mMockCi;

        mTestHandlerThread = new HandlerThread(TAG);
        mTestHandlerThread.start();
        mHandler = new Handler(mTestHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message message) {
                Log.d(TAG, "Received msg: " + message);
                mMessages.add(Message.obtain(message));
                mMessageLatch.countDown();
            }
        };

        mNetworkScanRequestTracker = new NetworkScanRequestTracker();
        mScanId = TelephonyScanManager.INVALID_SCAN_ID;
        setHasLocationPermissions(false);
        assertThat(mHandler).isNotNull();
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        stopNetworkScanIfNeeded(mScanId);
        mTestHandlerThread.quit();
        mMessages.clear();
        super.tearDown();
    }

    @Test
    public void testStartNetworkScan_nullRequest_shouldNeverScan() throws Exception {
        NetworkScanRequest request = null;
        Messenger messenger = new Messenger(mHandler);

        mScanId = mNetworkScanRequestTracker.startNetworkScan(true, request, messenger,
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when the request is null!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when no RadioAccessSpecifier is provided!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when empty RadioAccessSpecifier is "
                        + "provided!")).startNetworkScan(
                any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many "
                        + "RadioAccessSpecifiers!")).startNetworkScan(
                any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many bands!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many channels!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);

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
                    mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too small periodicity!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan with too large periodicity!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan with too small incremental results "
                        + "periodicity!")).startNetworkScan(
                any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan with too large incremental periodicity!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when periodicity is larger than max search"
                        + " time!")).startNetworkScan(
                any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when incremental results periodicity is "
                        + "larger than max search time!")).startNetworkScan(
                any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
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
                mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        verify(mPhone, never().description(
                "Phone should never start network scan when request with too many PLMNs!"))
                .startNetworkScan(any(), any());
        verifyMessage(TelephonyScanManager.CALLBACK_SCAN_ERROR, NetworkScan.ERROR_INVALID_SCAN,
                mScanId, null);
    }

    @Test
    public void testStartNetworkScan_succeed_returnValidScanId() throws Exception {
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        verify(mPhone).startNetworkScan(any(), any());
        assertThat(mScanId).isNotEqualTo(TelephonyScanManager.INVALID_SCAN_ID);
    }

    @Test
    public void testStartNetworkScan_succeed_deathRecipientIsLinked() throws Exception {
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        verify(mIBinder).linkToDeath(any(), anyInt());
    }

    @Test
    public void testStartNetworkScan_multipleRequests_scanIdShouldNotRepeat() throws Exception {
        int firstScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);
        stopNetworkScanIfNeeded(firstScanId);

        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        assertThat(mScanId).isNotEqualTo(firstScanId);
    }

    @Test
    public void testStartNetworkScan_singleResultAndSuccess_allMessagesNotified() throws Exception {
        mMessageLatch = new CountDownLatch(2); // 2 messages expected
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        // Only one result and it is completed.
        verifyStartNetworkScanAndEmulateScanResult(
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_COMPLETE,
                NetworkScan.SUCCESS, List.of(new CellInfoLte())));

        verifyMessages(
                // One RESTRICTED_SCAN_RESULTS msg follows by COMPLETE msg
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_RESTRICTED_SCAN_RESULTS,
                        NetworkScan.SUCCESS, mScanId, null),
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_COMPLETE,
                        NetworkScan.SUCCESS, mScanId, null)
        );
    }

    @Test
    public void testStartNetworkScan_resultFromCopiedRequest_noMessagesNotified() throws Exception {
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        // Scan result came but with copied (instead of original) NetworkScanRequestInfo
        NetworkScanRequestInfo obsoletedNsri = createNetworkScanRequestInfo(mScanId);
        verifyStartNetworkScanAndEmulateScanResult(obsoletedNsri,
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_COMPLETE,
                        NetworkScan.SUCCESS, List.of(new CellInfoLte())));

        // No message should send to client
        verifyMessages();
    }

    @Test
    public void testStartNetworkScan_multiResultsAndSuccess_allMessagesNotified() throws Exception {
        mMessageLatch = new CountDownLatch(4); // 4 messages expected
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        verifyStartNetworkScanAndEmulateScanResult(
                // First two results arrived but did not complete
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_PARTIAL,
                        NetworkScan.SUCCESS, List.of(new CellInfoLte())),
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_PARTIAL,
                        NetworkScan.SUCCESS, List.of(new CellInfoLte())),
                // Third result arrived and completed
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_COMPLETE,
                        NetworkScan.SUCCESS, List.of(new CellInfoLte())));

        verifyMessages(
                // Same number of SCAN_RESULTS and end with COMPLETE messages.
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_RESTRICTED_SCAN_RESULTS,
                        NetworkScan.SUCCESS, mScanId, null),
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_RESTRICTED_SCAN_RESULTS,
                        NetworkScan.SUCCESS, mScanId, null),
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_RESTRICTED_SCAN_RESULTS,
                        NetworkScan.SUCCESS, mScanId, null),
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_COMPLETE,
                        NetworkScan.SUCCESS, mScanId, null)
        );
    }

    @Test
    public void testStartNetworkScan_modemError_allMessagesNotified() throws Exception {
        mMessageLatch = new CountDownLatch(3); // 3 messages expected
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        verifyStartNetworkScanAndEmulateScanResult(
                // First result arrived but did not complete
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_PARTIAL,
                        NetworkScan.SUCCESS, List.of(new CellInfoLte())),
                // Final result arrived and indicated modem error
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_COMPLETE,
                        NetworkScan.ERROR_MODEM_ERROR, List.of(new CellInfoLte())));

        verifyMessages(
                // First SUCCESS scan result
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_RESTRICTED_SCAN_RESULTS,
                        NetworkScan.SUCCESS, mScanId, null),
                // Final ERROR messages
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_RESTRICTED_SCAN_RESULTS,
                        NetworkScan.ERROR_MODEM_ERROR, mScanId, null),
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_ERROR,
                        NetworkScan.ERROR_MODEM_ERROR, mScanId, null)
        );
    }

    @Test
    public void testStartNetworkScan_clientDied_shouldStopScan() throws Exception {
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        verifyStartNetworkScanAndEmulateBinderDied();

        verify(mPhone).stopNetworkScan(any());
    }

    @Test
    public void testStopNetworkScan_invalidScanId_throwIllegalArgumentException() throws Exception {
        final int invalidScanId = 1000;
        assertThrows(IllegalArgumentException.class,
                () -> mNetworkScanRequestTracker.stopNetworkScan(invalidScanId, CLIENT_UID));
    }

    @Test
    public void testStopNetworkScan_fromOtherUid_throwIllegalArgumentException() throws Exception {
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);

        assertThrows(IllegalArgumentException.class,
                () -> mNetworkScanRequestTracker.stopNetworkScan(mScanId, 654321));
    }

    @Test
    public void testStopNetworkScan_scanIdAndUidMatchWithoutError_shouldUnregister()
            throws Exception {
        mMessageLatch = new CountDownLatch(1); // 1 message expected
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);
        mNetworkScanRequestTracker.stopNetworkScan(mScanId, CLIENT_UID);
        processAllMessages();

        // No error during stopping network scan
        verifyStopNetworkScanAndEmulateResult(null /* commandException */);

        verify(mPhone.mCi).unregisterForNetworkScanResult(any());
        verify(mPhone.mCi).unregisterForModemReset(any());
        verify(mPhone.mCi).unregisterForNotAvailable(any());

        verifyMessages(
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_COMPLETE,
                        NetworkScan.SUCCESS, mScanId, null)
        );
    }

    @Test
    public void testStopNetworkScan_withError_reportTranslatedScanError()
            throws Exception {
        mMessageLatch = new CountDownLatch(1); // 1 message expected
        mScanId = scanNetworkWithOneShot(true /* renounceFineLocationAccess */);
        mNetworkScanRequestTracker.stopNetworkScan(mScanId, CLIENT_UID);
        processAllMessages();

        // No memory error during stopping network scan
        verifyStopNetworkScanAndEmulateResult(
                new CommandException(CommandException.Error.NO_MEMORY));

        verifyMessages(
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_ERROR,
                        NetworkScan.ERROR_MODEM_ERROR, mScanId, null)
        );
    }

    // -- Test cases below cover scenarios when caller has FINE_LOCATION permission --
    @Test
    public void testStartNetworkScan_withLocationPermission_allMessagesNotified()
            throws Exception {
        setHasLocationPermissions(true);
        mMessageLatch = new CountDownLatch(2); // 2 messages expected
        mScanId = scanNetworkWithOneShot(false /* renounceFineLocationAccess */);

        // Only one result and it is completed.
        verifyStartNetworkScanAndEmulateScanResult(
                new NetworkScanResult(NetworkScanResult.SCAN_STATUS_COMPLETE,
                        NetworkScan.SUCCESS, List.of(new CellInfoLte())));

        verifyMessages(
                // One SCAN_RESULTS msg follows by COMPLETE msg
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_RESULTS,
                        NetworkScan.SUCCESS, mScanId, null),
                Message.obtain(mHandler, TelephonyScanManager.CALLBACK_SCAN_COMPLETE,
                        NetworkScan.SUCCESS, mScanId, null)
        );
    }

    private void stopNetworkScanIfNeeded(int scanId) {
        if (scanId != TelephonyScanManager.INVALID_SCAN_ID) {
            try {
                mNetworkScanRequestTracker.stopNetworkScan(mScanId, CLIENT_UID);
                processAllMessages();
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void verifyMessages(Message... messages) {
        try {
            mMessageLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while latch is wait for messages");
        }

        assertThat(mMessages.size()).isEqualTo(messages.length);
        for (int i = 0; i < messages.length; i++) {
            // We only care about what/arg1/arg2/obj by now
            assertThat(mMessages.get(i).what).isEqualTo(messages[i].what);
            assertThat(mMessages.get(i).arg1).isEqualTo(messages[i].arg1);
            assertThat(mMessages.get(i).arg2).isEqualTo(messages[i].arg2);
            assertThat(mMessages.get(i).obj).isEqualTo(messages[i].obj);
        }
    }

    /**
     * Verify only one Message with better readability
     */
    private void verifyMessage(int what, int arg1, int arg2, Object obj) {
        verifyMessages(Message.obtain(mHandler, what, arg1, arg2, obj));
    }


    /**
     * Verify both {@link CommandsInterface#startNetworkScan(NetworkScanRequest, Message)} and
     * {@link CommandsInterface#registerForNetworkScanResult(Handler, int, Object)} and sends
     * emulated {@link NetworkScanResult} with original NetworkScanRequestInfo back through the
     * captures.
     */
    private void verifyStartNetworkScanAndEmulateScanResult(
            NetworkScanResult... networkScanResults) {
        NetworkScanRequestInfo nsri = verifyStartNetworkScanAndGetNetworkScanInfo();

        sendEmulatedScanResult(nsri, networkScanResults);
    }

    /**
     * Similar as verifyStartNetworkScanAndEmulateScanResult(NetworkScanResult...) above but
     * allows to set the customized NetworkScanRequestInfo other than original one.
     */
    private void verifyStartNetworkScanAndEmulateScanResult(
            NetworkScanRequestInfo nsri,
            NetworkScanResult... networkScanResults) {
        // Ignore the original NSRI returned
        verifyStartNetworkScanAndGetNetworkScanInfo();

        sendEmulatedScanResult(nsri, networkScanResults);
    }

    private void sendEmulatedScanResult(NetworkScanRequestInfo nsri,
            NetworkScanResult... networkScanResults) {
        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(mPhone.mCi).registerForNetworkScanResult(handlerCaptor.capture(), anyInt(), any());
        Handler handler = handlerCaptor.getValue();

        for (NetworkScanResult networkScanResult : networkScanResults) {
            Message result = Message.obtain(handler, EVENT_RECEIVE_NETWORK_SCAN_RESULT, nsri);
            AsyncResult.forMessage(result, networkScanResult, null);
            result.sendToTarget();
        }
        processAllMessages();
    }

    /**
     * Verify {@link CommandsInterface#startNetworkScan(NetworkScanRequest, Message)} and emulate
     * client process died through the captured {@link IBinder.DeathRecipient}.
     */
    private void verifyStartNetworkScanAndEmulateBinderDied() {
        IBinder.DeathRecipient nsri = verifyStartNetworkScanAndGetNetworkScanInfo();
        nsri.binderDied();
        processAllMessages();
    }

    /**
     * Verify {@link Phone#startNetworkScan(NetworkScanRequest, Message)} and
     * capture the NetworkScanRequestInfo for further usage.
     */
    private NetworkScanRequestInfo verifyStartNetworkScanAndGetNetworkScanInfo() {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mPhone).startNetworkScan(any(), messageCaptor.capture());
        Message responseMessage = messageCaptor.getValue();
        // NetworkScanRequestInfo is not public and can only be treated as IBinder.DeathRecipient
        NetworkScanRequestInfo nsri = (NetworkScanRequestInfo) responseMessage.obj;
        AsyncResult.forMessage(responseMessage, nsri, null);
        responseMessage.sendToTarget();
        processAllMessages();
        return nsri;
    }

    /**
     * Verify {@link  Phone#stopNetworkScan} and emulate the result (succeed or failures)
     * through the {@code commandException}.
     */
    private void verifyStopNetworkScanAndEmulateResult(CommandException commandException) {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mPhone).stopNetworkScan(messageCaptor.capture());
        Message responseMessage = messageCaptor.getValue();
        // NetworkScanRequestInfo is not public and can only be treated as Object here
        Object nsri = responseMessage.obj;
        AsyncResult.forMessage(responseMessage, nsri, commandException);
        responseMessage.sendToTarget();
        processAllMessages();
    }

    private int scanNetworkWithOneShot(boolean renounceFineLocationAccess) {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.EUTRAN, null,
                        null)
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
        int scanId = mNetworkScanRequestTracker.startNetworkScan(renounceFineLocationAccess,
                request, messenger, mIBinder, mPhone, CLIENT_UID, -1, CLIENT_PKG);
        processAllMessages();

        return scanId;
    }

    private NetworkScanRequestInfo createNetworkScanRequestInfo(int scanId) {
        RadioAccessSpecifier[] specifiers = new RadioAccessSpecifier[]{
                new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.EUTRAN, null,
                        null)
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
        return mNetworkScanRequestTracker.new NetworkScanRequestInfo(request, messenger, mIBinder,
                scanId, mPhone, CLIENT_UID, -1, CLIENT_PKG, true);
    }

    private void setHasLocationPermissions(boolean hasPermission) {
        if (!hasPermission) {
            // System location off, LocationAccessPolicy#checkLocationPermission returns DENIED_SOFT
            when(mLocationManager.isLocationEnabledForUser(any(UserHandle.class)))
                    .thenReturn(false);
        } else {
            // Turn on all to let LocationAccessPolicy#checkLocationPermission returns ALLOWED
            when(mContext.checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
            when(mContext.checkPermission(eq(Manifest.permission.ACCESS_COARSE_LOCATION),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
            when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OPSTR_FINE_LOCATION),
                    anyInt(), anyString(), nullable(String.class), nullable(String.class)))
                    .thenReturn(AppOpsManager.MODE_ALLOWED);
            when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OPSTR_COARSE_LOCATION),
                    anyInt(), anyString(), nullable(String.class), nullable(String.class)))
                    .thenReturn(AppOpsManager.MODE_ALLOWED);
            when(mLocationManager.isLocationEnabledForUser(any(UserHandle.class))).thenReturn(true);
            when(mContext.checkPermission(eq(Manifest.permission.INTERACT_ACROSS_USERS_FULL),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        }
    }
}
