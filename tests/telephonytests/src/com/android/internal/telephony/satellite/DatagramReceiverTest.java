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

package com.android.internal.telephony.satellite;

import android.provider.Telephony;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;

import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Pair;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.LinkedBlockingQueue;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DatagramReceiverTest extends TelephonyTest {
    private static final String TAG = "DatagramReceiverTest";
    private static final int SUB_ID = 0;
    private static final String TEST_MESSAGE = "This is a test datagram message";

    private DatagramReceiver mDatagramReceiverUT;
    private DatagramReceiver.SatelliteDatagramListenerHandler mSatelliteDatagramListenerHandler;

    @Mock private DatagramController mMockDatagramController;
    @Mock private SatelliteModemInterface mMockSatelliteModemInterface;


    /** Variables required to receive datagrams in the unit tests. */
    LinkedBlockingQueue<Integer> mResultListener;
    SatelliteDatagram mDatagram;
    InOrder mInOrder;
    private FakeSatelliteProvider mFakeSatelliteProvider;
    private MockContentResolver mMockContentResolver;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        // Setup mock satellite provider DB.
        mFakeSatelliteProvider = new FakeSatelliteProvider();
        mMockContentResolver = new MockContentResolver();
        mMockContentResolver.addProvider(
                Telephony.SatelliteDatagrams.PROVIDER_NAME, mFakeSatelliteProvider);
        doReturn(mMockContentResolver).when(mContext).getContentResolver();

        replaceInstance(DatagramController.class, "sInstance", null,
                mMockDatagramController);
        replaceInstance(SatelliteModemInterface.class, "sInstance", null,
                mMockSatelliteModemInterface);

        mDatagramReceiverUT = DatagramReceiver.make(mContext, Looper.myLooper(),
                mMockDatagramController);
        mSatelliteDatagramListenerHandler = new DatagramReceiver.SatelliteDatagramListenerHandler(
                Looper.myLooper(), SUB_ID);

        mResultListener = new LinkedBlockingQueue<>(1);
        mDatagram = new SatelliteDatagram(TEST_MESSAGE.getBytes());
        mInOrder = inOrder(mMockDatagramController);

        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        mDatagramReceiverUT.destroy();
        mDatagramReceiverUT = null;
        mResultListener = null;
        mDatagram = null;
        mInOrder = null;
        super.tearDown();
    }

    @Test
    public void testPollPendingSatelliteDatagrams_usingSatelliteModemInterface_success()
            throws Exception {
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];

            mDatagramReceiverUT.obtainMessage(2 /*EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE*/,
                            new AsyncResult(message.obj, null, null))
                    .sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).pollPendingSatelliteDatagrams(any(Message.class));

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
    }

    @Test
    public void testPollPendingSatelliteDatagrams_usingSatelliteModemInterface_failure()
            throws Exception {
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];

            mDatagramReceiverUT.obtainMessage(2 /*EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE*/,
                            new AsyncResult(message.obj, null,
                                    new SatelliteManager.SatelliteException(
                                            SatelliteManager.SATELLITE_SERVICE_ERROR)))
                    .sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).pollPendingSatelliteDatagrams(any(Message.class));

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED),
                        eq(0), eq(SatelliteManager.SATELLITE_SERVICE_ERROR));

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_SERVICE_ERROR);
    }

    @Test
    public void testPollPendingSatelliteDatagrams_usingCommandsInterface_phoneNull()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {null});

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED),
                        eq(0), eq(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));

        assertThat(mResultListener.peek())
                .isEqualTo(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
    }

    @Test
    public void testPollPendingSatelliteDatagrams_usingCommandsInterface_success()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone});
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];

            mDatagramReceiverUT.obtainMessage(2 /*EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE*/,
                            new AsyncResult(message.obj, null, null))
                    .sendToTarget();
            return null;
        }).when(mPhone).pollPendingSatelliteDatagrams(any(Message.class));

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
    }

    @Test
    public void testPollPendingSatelliteDatagrams_usingCommandsInterface_failure()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone});
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];

            mDatagramReceiverUT.obtainMessage(2 /*EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE*/,
                            new AsyncResult(message.obj, null,
                                    new SatelliteManager.SatelliteException(
                                            SatelliteManager.SATELLITE_SERVICE_ERROR)))
                    .sendToTarget();
            return null;
        }).when(mPhone).pollPendingSatelliteDatagrams(any(Message.class));

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING), eq(0),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED),
                        eq(0), eq(SatelliteManager.SATELLITE_SERVICE_ERROR));

        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_SERVICE_ERROR);
    }

    @Test
    public void testSatelliteDatagramReceived_receiveNone() {
        mSatelliteDatagramListenerHandler.obtainMessage(1 /*EVENT_SATELLITE_DATAGRAM_RECEIVED*/,
                new AsyncResult(null, new Pair<>(null, 0), null))
                .sendToTarget();

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_NONE),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));
    }

    @Test
    public void testSatelliteDatagramReceived_success_zeroPendingCount() {
        mSatelliteDatagramListenerHandler.obtainMessage(1 /*EVENT_SATELLITE_DATAGRAM_RECEIVED*/,
                        new AsyncResult(null, new Pair<>(mDatagram, 0), null))
                .sendToTarget();

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));
    }

    @Test
    public void testSatelliteDatagramReceived_success_nonZeroPendingCount() {
        mSatelliteDatagramListenerHandler.obtainMessage(1 /*EVENT_SATELLITE_DATAGRAM_RECEIVED*/,
                        new AsyncResult(null, new Pair<>(mDatagram, 10), null))
                .sendToTarget();

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS),
                        eq(10), eq(SatelliteManager.SATELLITE_ERROR_NONE));
    }
}
