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

import android.os.Bundle;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.satellite.ISatelliteTransmissionUpdateCallback;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteManager.SatelliteException;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PointingAppControllerTest extends TelephonyTest {
    private static final String TAG = "PointingAppControllerTest";
    private static final int SUB_ID = 0;
    private static final long TIMEOUT = 500;

    //Events For SatelliteControllerHandler
    private static final int EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE = 2;
    private static final int EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE = 4;
    private static final String KEY_POINTING_UI_PACKAGE_NAME = "default_pointing_ui_package";
    private static final String KEY_POINTING_UI_CLASS_NAME = "default_pointing_ui_class";
    private static final String KEY_NEED_FULL_SCREEN = "needFullScreen";

    private PointingAppController mPointingAppController;
    InOrder mInOrder;

    @Mock private SatelliteModemInterface mMockSatelliteModemInterface;
    @Mock private SatelliteController mMockSatelliteController;

    private TestSatelliteTransmissionUpdateCallback mSatelliteTransmissionUpdateCallback;
    private TestSatelliteControllerHandler mTestSatelliteControllerHandler;
    /** Variables required to receive datagrams in the unit tests. */
    LinkedBlockingQueue<Integer> mResultListener;
    int mResultCode = -1;
    private Semaphore mSendDatagramStateSemaphore = new Semaphore(0);
    private Semaphore mReceiveDatagramStateSemaphore = new Semaphore(0);

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(SatelliteModemInterface.class, "sInstance", null,
                mMockSatelliteModemInterface);
        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
        mPointingAppController = new PointingAppController(mContext);
        mContextFixture.putResource(R.string.config_pointing_ui_package,
                KEY_POINTING_UI_PACKAGE_NAME);
        mContextFixture.putResource(R.string.config_pointing_ui_class,
                KEY_POINTING_UI_CLASS_NAME);
        mResultListener = new LinkedBlockingQueue<>(1);
        mSatelliteTransmissionUpdateCallback = new TestSatelliteTransmissionUpdateCallback();
        mTestSatelliteControllerHandler = new TestSatelliteControllerHandler();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        mResultListener = null;

        mSatelliteTransmissionUpdateCallback = null;
        super.tearDown();
    }

    private class TestSatelliteControllerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch(msg.what) {
                case EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE: {
                    ar = (AsyncResult) msg.obj;
                    mResultCode =  SatelliteServiceUtils.getSatelliteError(ar,
                            "startSatelliteTransmissionUpdates");
                    logd("mResultCode = " + mResultCode);
                    break;
                }
                case EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE: {
                    ar = (AsyncResult) msg.obj;
                    mResultCode =  SatelliteServiceUtils.getSatelliteError(ar,
                            "stopSatelliteTransmissionUpdates");
                    logd("mResultCode = " + mResultCode);
                    break;
                }
                default:
                    logd("TestSatelliteController: " + msg.what);
                    break;
            }
        }
    }
    private class TestSatelliteTransmissionUpdateCallback
                                extends ISatelliteTransmissionUpdateCallback.Stub {
        int mState;
        int mSendPendingCount;
        int mReceivePendingCount;
        int mErrorCode;
        public boolean inSendDatagramStateCallback = false;
        public boolean inReceiveDatagramStateCallback = false;

        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            //Not Used
        }

        @Override
        public void onSendDatagramStateChanged(int state, int sendPendingCount,
                                    int errorCode) {
            mState = state;
            mSendPendingCount = sendPendingCount;
            mErrorCode = errorCode;
            inSendDatagramStateCallback = true;
            try {
                mSendDatagramStateSemaphore.release();
            } catch (Exception ex) {
                loge("mSendDatagramStateSemaphore: Got exception in releasing semaphore, ex=" + ex);
            }
        }

        @Override
        public void onReceiveDatagramStateChanged(int state,
                            int receivePendingCount, int errorCode) {
            mState = state;
            mReceivePendingCount = receivePendingCount;
            mErrorCode = errorCode;
            inReceiveDatagramStateCallback = true;
            try {
                mReceiveDatagramStateSemaphore.release();
            } catch (Exception ex) {
                loge("mReceiveDatagramStateSemaphore: Got exception in releasing semaphore, ex="
                        + ex);
            }
        }

        public int getState() {
            return mState;
        }

        public int getSendPendingCount() {
            return mSendPendingCount;
        }

        public int getReceivePendingCount() {
            return mReceivePendingCount;
        }

        public int getErrorCode() {
            return mErrorCode;
        }
    }

    private boolean waitForReceiveDatagramStateChangedRessult(
            int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mReceiveDatagramStateSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive "
                            + "ReceiveDatagramStateChanged event");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForReceiveDatagramStateChangedRessult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private boolean waitForSendDatagramStateChangedRessult(
            int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSendDatagramStateSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive "
                            + "SendDatagramStateChanged event");
                    return false;
                }
            } catch (Exception ex) {
                loge("waitForSendDatagramStateChangedRessult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private void setUpResponseForStartTransmissionUpdates(
            @SatelliteManager.SatelliteError int error) {
        SatelliteException exception = (error == SatelliteManager.SATELLITE_ERROR_NONE)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mPhone).startSatellitePositionUpdates(any(Message.class));

        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).startSendingSatellitePointingInfo(any(Message.class));
    }

    private void setUpResponseForStopTransmissionUpdates(
            @SatelliteManager.SatelliteError int error) {
        SatelliteException exception = (error == SatelliteManager.SATELLITE_ERROR_NONE)
                ? null : new SatelliteException(error);
        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mPhone).stopSatellitePositionUpdates(any(Message.class));

        doAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(message, null, exception);
            message.sendToTarget();
            return null;
        }).when(mMockSatelliteModemInterface).stopSendingSatellitePointingInfo(any(Message.class));
    }

    @Test
    public void testStartSatelliteTransmissionUpdates_CommandInterface()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        Message testMessage = mTestSatelliteControllerHandler
                .obtainMessage(EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE, null);
        setUpResponseForStartTransmissionUpdates(SatelliteManager.SATELLITE_ERROR_NONE);
        mPointingAppController.startSatelliteTransmissionUpdates(testMessage, mPhone);

        processAllMessages();

        verify(mMockSatelliteModemInterface, never())
                .startSendingSatellitePointingInfo(eq(testMessage));

        verify(mPhone)
                .startSatellitePositionUpdates(eq(testMessage));

        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, mResultCode);

        assertTrue(mPointingAppController.getStartedSatelliteTransmissionUpdates());
    }

    @Test
    public void testStartSatelliteTransmissionUpdates_success()
            throws Exception {
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mPointingAppController.setStartedSatelliteTransmissionUpdates(false);
        Message testMessage = mTestSatelliteControllerHandler
                .obtainMessage(EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE, null);
        setUpResponseForStartTransmissionUpdates(SatelliteManager.SATELLITE_ERROR_NONE);
        mPointingAppController.startSatelliteTransmissionUpdates(testMessage, mPhone);

        verify(mMockSatelliteModemInterface)
                .startSendingSatellitePointingInfo(eq(testMessage));

        verify(mPhone, never())
                .startSatellitePositionUpdates(eq(testMessage));

        processAllMessages();


        assertTrue(mPointingAppController.getStartedSatelliteTransmissionUpdates());
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, mResultCode);
    }

    @Test
    public void testStartSatelliteTransmissionUpdates_phoneNull()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mPointingAppController.setStartedSatelliteTransmissionUpdates(false);
        Message testMessage = mTestSatelliteControllerHandler
                .obtainMessage(EVENT_START_SATELLITE_TRANSMISSION_UPDATES_DONE, null);

        mPointingAppController.startSatelliteTransmissionUpdates(testMessage, null);
        processAllMessages();
        verify(mMockSatelliteModemInterface, never())
                .startSendingSatellitePointingInfo(eq(testMessage));

        verify(mPhone, never())
                .startSatellitePositionUpdates(eq(testMessage));

        assertFalse(mPointingAppController.getStartedSatelliteTransmissionUpdates());

        assertEquals(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, mResultCode);
    }

    @Test
    public void testStopSatelliteTransmissionUpdates_CommandInterface()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopTransmissionUpdates(SatelliteManager.SATELLITE_ERROR_NONE);
        Message testMessage = mTestSatelliteControllerHandler
                .obtainMessage(EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE, null);
        mPointingAppController.stopSatelliteTransmissionUpdates(testMessage, mPhone);

        processAllMessages();

        verify(mMockSatelliteModemInterface, never())
                .stopSendingSatellitePointingInfo(eq(testMessage));

        verify(mPhone)
                .stopSatellitePositionUpdates(eq(testMessage));

        assertFalse(mPointingAppController.getStartedSatelliteTransmissionUpdates());

        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, mResultCode);
    }

    @Test
    public void testStopSatelliteTransmissionUpdates_success()
            throws Exception {
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        setUpResponseForStopTransmissionUpdates(SatelliteManager.SATELLITE_ERROR_NONE);
        Message testMessage = mTestSatelliteControllerHandler
                .obtainMessage(EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE, null);
        mPointingAppController.stopSatelliteTransmissionUpdates(testMessage, mPhone);

        processAllMessages();

        verify(mMockSatelliteModemInterface)
                .stopSendingSatellitePointingInfo(eq(testMessage));

        verify(mPhone, never())
                .stopSatellitePositionUpdates(eq(testMessage));

        assertFalse(mPointingAppController.getStartedSatelliteTransmissionUpdates());
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, mResultCode);
    }

    @Test
    public void testStopSatellitePointingInfo_phoneNull()
            throws Exception {
        doReturn(false).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        Message testMessage = mTestSatelliteControllerHandler
                .obtainMessage(EVENT_STOP_SATELLITE_TRANSMISSION_UPDATES_DONE, null);
        mPointingAppController.stopSatelliteTransmissionUpdates(testMessage, null);

        processAllMessages();

        verify(mMockSatelliteModemInterface, never())
                .stopSendingSatellitePointingInfo(eq(testMessage));

        verify(mPhone, never())
                .stopSatellitePositionUpdates(eq(testMessage));

        assertEquals(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, mResultCode);

    }

    @Test
    public void testStartPointingUI() throws Exception {
        ArgumentCaptor<Intent> startedIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        mPointingAppController.startPointingUI(true);
        verify(mContext).startActivity(startedIntentCaptor.capture());
        Intent intent = startedIntentCaptor.getValue();
        assertEquals(KEY_POINTING_UI_PACKAGE_NAME, intent.getComponent().getPackageName());
        assertEquals(KEY_POINTING_UI_CLASS_NAME, intent.getComponent().getClassName());
        Bundle b = intent.getExtras();
        assertTrue(b.containsKey(KEY_NEED_FULL_SCREEN));
        assertTrue(b.getBoolean(KEY_NEED_FULL_SCREEN));
    }

    @Test
    public void testUpdateSendDatagramTransferState() throws Exception {
        mPointingAppController.registerForSatelliteTransmissionUpdates(SUB_ID,
                mSatelliteTransmissionUpdateCallback, mPhone);
        mPointingAppController.updateSendDatagramTransferState(SUB_ID,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS, 1,
                SatelliteManager.SATELLITE_ERROR_NONE);
        assertTrue(waitForSendDatagramStateChangedRessult(1));
        assertEquals(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                mSatelliteTransmissionUpdateCallback.getState());
        assertEquals(1, mSatelliteTransmissionUpdateCallback.getSendPendingCount());
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE,
                mSatelliteTransmissionUpdateCallback.getErrorCode());
        assertTrue(mSatelliteTransmissionUpdateCallback.inSendDatagramStateCallback);
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(SUB_ID,
                    mResultListener::offer, mSatelliteTransmissionUpdateCallback, mPhone);
        mResultListener.clear();
    }

    @Test
    public void testUpdateReceiveDatagramTransferState() throws Exception {
        mPointingAppController.registerForSatelliteTransmissionUpdates(SUB_ID,
                mSatelliteTransmissionUpdateCallback, mPhone);
        mPointingAppController.updateReceiveDatagramTransferState(SUB_ID,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS, 2,
                SatelliteManager.SATELLITE_ERROR_NONE);
        assertTrue(waitForReceiveDatagramStateChangedRessult(1));
        assertEquals(SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                mSatelliteTransmissionUpdateCallback.getState());
        assertEquals(2, mSatelliteTransmissionUpdateCallback.getReceivePendingCount());
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE,
                mSatelliteTransmissionUpdateCallback.getErrorCode());
        assertTrue(mSatelliteTransmissionUpdateCallback.inReceiveDatagramStateCallback);
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(SUB_ID,
                    mResultListener::offer, mSatelliteTransmissionUpdateCallback, mPhone);
        mResultListener.clear();
    }

    @Test
    public void testRegisterForSatelliteTransmissionUpdates_CommandInterface() throws Exception {
        mResultListener.clear();
        mInOrder = inOrder(mPhone);
        TestSatelliteTransmissionUpdateCallback callback1 = new
                TestSatelliteTransmissionUpdateCallback();
        TestSatelliteTransmissionUpdateCallback callback2 = new
                TestSatelliteTransmissionUpdateCallback();
        int subId1 = 1;
        int subId2 = 2;
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId1,
                callback1, mPhone);
        mInOrder.verify(mPhone).registerForSatellitePositionInfoChanged(any(),
                eq(1), eq(null));
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId1,
                callback2, mPhone);
        mInOrder.verify(mPhone, never()).registerForSatellitePositionInfoChanged(any(),
                eq(1), eq(null));
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId2,
                callback1, mPhone);
        mInOrder.verify(mPhone).registerForSatellitePositionInfoChanged(any(),
                eq(1), eq(null));
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId2,
                callback2, mPhone);
        mInOrder.verify(mPhone, never()).registerForSatellitePositionInfoChanged(any(),
                eq(1), eq(null));
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId1,
                mResultListener::offer, callback1, mPhone);
        processAllMessages();
        //since there are 2 callbacks registered for this sub_id, Handler is not unregistered
        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
        mResultListener.remove();
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId1,
                mResultListener::offer, callback2, mPhone);
        mInOrder.verify(mPhone).unregisterForSatellitePositionInfoChanged(any(Handler.class));
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId2,
                mResultListener::offer, callback1, mPhone);
        processAllMessages();
        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
        mResultListener.remove();
        mInOrder.verify(mPhone, never()).unregisterForSatellitePositionInfoChanged(
                any(Handler.class));
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId2,
                mResultListener::offer, callback2, null);
        processAllMessages();
        assertThat(mResultListener.peek())
                .isEqualTo(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
        mResultListener.remove();
        mInOrder = null;
    }

    @Test
    public void testRegisterForSatelliteTransmissionUpdates() throws Exception {
        mResultListener.clear();
        doReturn(true).when(mMockSatelliteModemInterface).isSatelliteServiceSupported();
        mInOrder = inOrder(mMockSatelliteModemInterface);
        TestSatelliteTransmissionUpdateCallback callback1 = new
                TestSatelliteTransmissionUpdateCallback();
        TestSatelliteTransmissionUpdateCallback callback2 = new
                TestSatelliteTransmissionUpdateCallback();
        int subId1 = 3;
        int subId2 = 4;
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId1,
                callback1, mPhone);
        mInOrder.verify(mMockSatelliteModemInterface).registerForSatellitePositionInfoChanged(any(),
                eq(1), eq(null));
        mInOrder.verify(mMockSatelliteModemInterface).registerForDatagramTransferStateChanged(any(),
                eq(4), eq(null));
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId1,
                callback2, mPhone);
        mInOrder.verify(mMockSatelliteModemInterface, never())
                .registerForSatellitePositionInfoChanged(any(), eq(1), eq(null));
        mInOrder.verify(mMockSatelliteModemInterface, never())
                .registerForDatagramTransferStateChanged(any(), eq(4), eq(null));
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId2,
                callback1, mPhone);
        mInOrder.verify(mMockSatelliteModemInterface).registerForSatellitePositionInfoChanged(any(),
                eq(1), eq(null));
        mInOrder.verify(mMockSatelliteModemInterface).registerForDatagramTransferStateChanged(any(),
                eq(4), eq(null));
        mPointingAppController.registerForSatelliteTransmissionUpdates(subId2,
                callback2, mPhone);
        mInOrder.verify(mMockSatelliteModemInterface, never())
                .registerForSatellitePositionInfoChanged(any(), eq(1), eq(null));
        mInOrder.verify(mMockSatelliteModemInterface, never())
                .registerForDatagramTransferStateChanged(any(), eq(4), eq(null));
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId1,
                mResultListener::offer, callback1, mPhone);
        processAllMessages();
        //since there are 2 callbacks registered for this sub_id, Handler is not unregistered
        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
        mResultListener.remove();
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId1,
                mResultListener::offer, callback2, mPhone);
        mInOrder.verify(mMockSatelliteModemInterface).unregisterForSatellitePositionInfoChanged(
                any(Handler.class));
        mInOrder.verify(mMockSatelliteModemInterface).unregisterForDatagramTransferStateChanged(
                any(Handler.class));
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId2,
                mResultListener::offer, callback1, mPhone);
        processAllMessages();
        assertThat(mResultListener.peek()).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
        mResultListener.remove();
        mInOrder.verify(mMockSatelliteModemInterface, never())
                .unregisterForSatellitePositionInfoChanged(any(Handler.class));
        mInOrder.verify(mMockSatelliteModemInterface, never())
                .unregisterForDatagramTransferStateChanged(any(Handler.class));
        mPointingAppController.unregisterForSatelliteTransmissionUpdates(subId2,
                mResultListener::offer, callback2, null);
        processAllMessages();
        mInOrder.verify(mMockSatelliteModemInterface).unregisterForSatellitePositionInfoChanged(
                any(Handler.class));
        mInOrder.verify(mMockSatelliteModemInterface).unregisterForDatagramTransferStateChanged(
                any(Handler.class));
        mInOrder = null;
    }

    private static void loge(String message) {
        Log.e(TAG, message);
    }
}
