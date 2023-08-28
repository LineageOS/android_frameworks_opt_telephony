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

import static com.android.internal.telephony.satellite.DatagramController.SATELLITE_ALIGN_TIMEOUT;

import android.annotation.NonNull;
import android.content.Context;
import android.provider.Telephony;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Pair;

import com.android.internal.telephony.IVoidConsumer;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.satellite.metrics.ControllerMetricsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DatagramReceiverTest extends TelephonyTest {
    private static final String TAG = "DatagramReceiverTest";
    private static final int SUB_ID = 0;
    private static final String TEST_MESSAGE = "This is a test datagram message";
    private static final long TEST_EXPIRE_TIMER_SATELLITE_ALIGN = TimeUnit.SECONDS.toMillis(1);

    private DatagramReceiver mDatagramReceiverUT;
    private DatagramReceiver.SatelliteDatagramListenerHandler mSatelliteDatagramListenerHandler;
    private TestDatagramReceiver mTestDemoModeDatagramReceiver;

    @Mock private SatelliteController mMockSatelliteController;
    @Mock private DatagramController mMockDatagramController;
    @Mock private SatelliteModemInterface mMockSatelliteModemInterface;
    @Mock private ControllerMetricsStats mMockControllerMetricsStats;

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

        replaceInstance(SatelliteController.class, "sInstance", null, mMockSatelliteController);
        replaceInstance(DatagramController.class, "sInstance", null,
                mMockDatagramController);
        replaceInstance(SatelliteModemInterface.class, "sInstance", null,
                mMockSatelliteModemInterface);
        replaceInstance(ControllerMetricsStats.class, "sInstance", null,
                mMockControllerMetricsStats);

        mDatagramReceiverUT = DatagramReceiver.make(mContext, Looper.myLooper(),
                mMockDatagramController);
        mTestDemoModeDatagramReceiver = new TestDatagramReceiver(mContext, Looper.myLooper(),
                mMockDatagramController);
        mSatelliteDatagramListenerHandler = new DatagramReceiver.SatelliteDatagramListenerHandler(
                Looper.myLooper(), SUB_ID);

        mResultListener = new LinkedBlockingQueue<>(1);
        mDatagram = new SatelliteDatagram(TEST_MESSAGE.getBytes());
        mInOrder = inOrder(mMockDatagramController);

        when(mMockDatagramController.isSendingInIdleState()).thenReturn(true);
        when(mMockDatagramController.isPollingInIdleState()).thenReturn(true);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        mFakeSatelliteProvider.shutdown();
        mDatagramReceiverUT.destroy();
        mDatagramReceiverUT = null;
        mTestDemoModeDatagramReceiver = null;
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

    @Test
    public void testPollPendingSatelliteDatagrams_DemoMode_Align_succeed() throws Exception {
        // Checks invalid case only as SatelliteController does not exist in unit test
        mTestDemoModeDatagramReceiver.setDemoMode(true);
        mTestDemoModeDatagramReceiver.onDeviceAlignedWithSatellite(true);
        when(mMockDatagramController.getDemoModeDatagram()).thenReturn(mDatagram);
        mTestDemoModeDatagramReceiver.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);
        processAllMessages();
        verify(mMockDatagramController, times(1)).getDemoModeDatagram();
        verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING),
                        anyInt(),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED),
                        anyInt(),
                        eq(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE));
        verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        anyInt(),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(mResultListener.peek())
                .isEqualTo(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
    }

    @Test
    public void testPollPendingSatelliteDatagrams_DemoMode_Align_failed() throws Exception {
        // Checks invalid case only as SatelliteController does not exist in unit test
        long previousTimer = mTestDemoModeDatagramReceiver.getSatelliteAlignedTimeoutDuration();
        mTestDemoModeDatagramReceiver.setDemoMode(true);
        mTestDemoModeDatagramReceiver.setDuration(TEST_EXPIRE_TIMER_SATELLITE_ALIGN);
        mTestDemoModeDatagramReceiver.onDeviceAlignedWithSatellite(false);
        when(mMockDatagramController.getDemoModeDatagram()).thenReturn(mDatagram);
        mTestDemoModeDatagramReceiver.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);
        processAllMessages();
        verify(mMockDatagramController, never()).getDemoModeDatagram();
        verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING),
                        anyInt(),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        processAllFutureMessages();
        verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED),
                        anyInt(),
                        eq(SatelliteManager.SATELLITE_NOT_REACHABLE));
        verify(mMockDatagramController)
                .updateReceiveStatus(eq(SUB_ID),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        anyInt(),
                        eq(SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(mResultListener.peek())
                .isEqualTo(SatelliteManager.SATELLITE_NOT_REACHABLE);

        mTestDemoModeDatagramReceiver.setDemoMode(false);
        mTestDemoModeDatagramReceiver.onDeviceAlignedWithSatellite(false);
        mTestDemoModeDatagramReceiver.setDuration(previousTimer);
    }

    @Test
    public void testSatelliteModemBusy_modemSendingDatagram_pollingFailure() {
        when(mMockDatagramController.isSendingInIdleState()).thenReturn(false);

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);
        processAllMessages();
        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_MODEM_BUSY);
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagrams_pollingFailure() {
        when(mMockDatagramController.isSendingInIdleState()).thenReturn(false);
        when(mMockDatagramController.isPollingInIdleState()).thenReturn(true);

        mDatagramReceiverUT.pollPendingSatelliteDatagrams(SUB_ID, mResultListener::offer);
        processAllMessages();
        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_MODEM_BUSY);
    }

    @Test
    public void testOnSatelliteModemStateChanged_modemStateIdle() {
        mDatagramReceiverUT.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        processAllMessages();
        verifyNoMoreInteractions(mMockDatagramController);
    }

    @Test
    public void testOnSatelliteModemStateChanged_modemStateOff_modemReceivingDatagrams() {
        when(mMockDatagramController.isReceivingDatagrams()).thenReturn(true);
        when(mMockDatagramController.getReceivePendingCount()).thenReturn(10);

        mDatagramReceiverUT.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(anyInt(),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED),
                        eq(10), eq(SatelliteManager.SATELLITE_REQUEST_ABORTED));
        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(anyInt(),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));
    }

    @Test
    public void testOnSatelliteModemStateChanged_modemStateOff_modemNotReceivingDatagrams() {
        when(mMockDatagramController.isReceivingDatagrams()).thenReturn(false);

        mDatagramReceiverUT.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);

        processAllMessages();

        mInOrder.verify(mMockDatagramController)
                .updateReceiveStatus(anyInt(),
                        eq(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE),
                        eq(0), eq(SatelliteManager.SATELLITE_ERROR_NONE));
    }

    @Test
    public void testRegisterForSatelliteDatagram_satelliteNotSupported() {
        when(mMockSatelliteController.isSatelliteSupported()).thenReturn(false);

        ISatelliteDatagramCallback callback = new ISatelliteDatagramCallback() {
            @Override
            public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                    int pendingCount, IVoidConsumer callback) throws RemoteException {
                logd("onSatelliteDatagramReceived");
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        };

        assertThat(mDatagramReceiverUT.registerForSatelliteDatagram(SUB_ID, callback))
                .isEqualTo(SatelliteManager.SATELLITE_NOT_SUPPORTED);
    }

    private static class TestDatagramReceiver extends DatagramReceiver {
        private long mLong =  SATELLITE_ALIGN_TIMEOUT;

        TestDatagramReceiver(@NonNull Context context, @NonNull Looper looper,
                @NonNull DatagramController datagramController) {
            super(context, looper, datagramController);
        }

        @Override
        protected void setDemoMode(boolean isDemoMode) {
            super.setDemoMode(isDemoMode);
        }

        @Override
        protected void onDeviceAlignedWithSatellite(boolean isAligned) {
            super.onDeviceAlignedWithSatellite(isAligned);
        }

        @Override
        protected long getSatelliteAlignedTimeoutDuration() {
            return mLong;
        }

        public void setDuration(long duration) {
            mLong = duration;
        }
    }
}
