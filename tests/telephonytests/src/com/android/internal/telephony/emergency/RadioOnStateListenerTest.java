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

package com.android.internal.telephony.emergency;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.AsyncResult;
import android.os.Handler;
import android.telephony.ServiceState;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests the RadioOnStateListener, which listens to one Phone and waits until its service state
 * changes to accepting emergency calls or in service. If it can not find a tower to camp onto for
 * emergency calls, then it will fail after a timeout period.
 */
@RunWith(AndroidJUnit4.class)
public class RadioOnStateListenerTest extends TelephonyTest {

    private static final long TIMEOUT_MS = 1000;

    @Mock Phone mMockPhone;
    @Mock RadioOnStateListener.Callback mCallback;
    @Mock CommandsInterface mMockCi;
    RadioOnStateListener mListener;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        mListener = new RadioOnStateListener();
        doReturn(mSST).when(mMockPhone).getServiceStateTracker();
        mMockPhone.mCi = mMockCi;
    }

    @After
    public void tearDown() throws Exception {
        mListener.setTimeBetweenRetriesMillis(5000);
        mListener.setMaxNumRetries(5);
        mListener.getHandler().removeCallbacksAndMessages(null);
        // Wait for the queue to clear...
        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS /* ms timeout */);
        mListener = null;
        super.tearDown();
    }

    /**
     * Ensure that we successfully register for the ServiceState changed messages in Telephony.
     */
    @Test
    public void testRegisterForCallback() {
        mListener.waitForRadioOn(mMockPhone, mCallback, false, false, 0);

        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);

        verify(mMockPhone).unregisterForServiceStateChanged(any(Handler.class));
        verify(mSatelliteController).unregisterForSatelliteModemStateChanged(anyInt(), any());
        verify(mMockPhone).registerForServiceStateChanged(any(Handler.class),
                eq(RadioOnStateListener.MSG_SERVICE_STATE_CHANGED), isNull());
        verify(mSatelliteController, never()).registerForSatelliteModemStateChanged(
                anyInt(), any());

        verify(mMockCi).registerForOffOrNotAvailable(any(Handler.class),
                eq(RadioOnStateListener.MSG_RADIO_OFF_OR_NOT_AVAILABLE), isNull());
    }

    /**
     * Ensure that we successfully register for the satellite modem state changed messages.
     */
    @Test
    public void testRegisterForSatelliteCallback() {
        doReturn(true).when(mSatelliteController).isSatelliteEnabled();
        mListener.waitForRadioOn(mMockPhone, mCallback, false, false, 0);

        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);

        verify(mSatelliteController).unregisterForSatelliteModemStateChanged(anyInt(), any());
        verify(mSatelliteController).registerForSatelliteModemStateChanged(anyInt(), any());
    }

    /**
     * {@link RadioOnStateListener.Callback#isOkToCall(Phone, int, boolean)} returns true after
     * service state changes, so we are expecting
     * {@link RadioOnStateListener.Callback#onComplete(RadioOnStateListener, boolean)} to
     * return true.
     */
    @Test
    public void testPhoneChangeState_OkToCallTrue() {
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_IN_SERVICE);
        when(mMockPhone.getServiceState()).thenReturn(state);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean())).thenReturn(true);
        mListener.waitForRadioOn(mMockPhone, mCallback, false, false, 0);
        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);

        mListener.getHandler().obtainMessage(RadioOnStateListener.MSG_SERVICE_STATE_CHANGED,
                new AsyncResult(null, state, null)).sendToTarget();

        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);
        verify(mCallback).onComplete(eq(mListener), eq(true));
    }

    /**
     * {@link RadioOnStateListener.Callback#isOkToCall(Phone, int, boolean)} returns true after
     * satellite modem state changes, so we are expecting
     * {@link RadioOnStateListener.Callback#onComplete(RadioOnStateListener, boolean)} to
     * return true.
     */
    @Test
    public void testSatelliteChangeState_OkToCallTrue() {
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_IN_SERVICE);
        when(mMockPhone.getServiceState()).thenReturn(state);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean())).thenReturn(true);
        mListener.waitForRadioOn(mMockPhone, mCallback, false, false, 0);
        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);

        mListener.getHandler().obtainMessage(RadioOnStateListener.MSG_SATELLITE_ENABLED_CHANGED)
                .sendToTarget();

        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);
        verify(mCallback).onComplete(eq(mListener), eq(true));
    }

    /**
     * We never receive a
     * {@link RadioOnStateListener.Callback#onComplete(RadioOnStateListener, boolean)} because
     * {@link RadioOnStateListener.Callback#isOkToCall(Phone, int, boolean)} returns false.
     */
    @Test
    public void testPhoneChangeState_NoOkToCall_Timeout() {
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_OUT_OF_SERVICE);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean())).thenReturn(false);
        when(mMockPhone.getServiceState()).thenReturn(state);
        mListener.waitForRadioOn(mMockPhone, mCallback, false, false, 0);
        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);

        mListener.getHandler().obtainMessage(RadioOnStateListener.MSG_SERVICE_STATE_CHANGED,
                new AsyncResult(null, state, null)).sendToTarget();

        waitForHandlerAction(mListener.getHandler(), TIMEOUT_MS);
        verify(mCallback, never()).onComplete(any(RadioOnStateListener.class), anyBoolean());
    }

    /**
     * Tests {@link RadioOnStateListener.Callback#isOkToCall(Phone, int, boolean)} returning
     * false and hitting the max number of retries. This should result in
     * {@link RadioOnStateListener.Callback#onComplete(RadioOnStateListener, boolean)} returning
     * false.
     */
    @Test
    public void testTimeout_RetryFailure() {
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_POWER_OFF);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mMockPhone.getServiceState()).thenReturn(state);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean())).thenReturn(false);
        mListener.setTimeBetweenRetriesMillis(0/* ms */);
        mListener.setMaxNumRetries(2);

        // Wait for the timer to expire and check state manually in onRetryTimeout
        mListener.waitForRadioOn(mMockPhone, mCallback, false, false, 0);
        waitForDelayedHandlerAction(mListener.getHandler(), TIMEOUT_MS /* delay */, TIMEOUT_MS);

        verify(mCallback).onComplete(eq(mListener), eq(false));
        verify(mMockPhone, times(2)).setRadioPower(eq(true), eq(false), eq(false), eq(false));
        verify(mSatelliteController, never()).requestSatelliteEnabled(
                anyInt(), eq(false), eq(false), any());
    }

    @Test
    public void testTimeout_RetryFailure_ForEmergency() {
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_POWER_OFF);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mMockPhone.getServiceState()).thenReturn(state);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean())).thenReturn(false);
        mListener.setTimeBetweenRetriesMillis(0/* ms */);
        mListener.setMaxNumRetries(2);

        // Wait for the timer to expire and check state manually in onRetryTimeout
        mListener.waitForRadioOn(mMockPhone, mCallback, true, true, 0);
        waitForDelayedHandlerAction(mListener.getHandler(), TIMEOUT_MS /* delay */, TIMEOUT_MS);

        verify(mCallback).onComplete(eq(mListener), eq(false));
        verify(mMockPhone, times(2)).setRadioPower(eq(true), eq(true), eq(true), eq(false));
        verify(mSatelliteController, never()).requestSatelliteEnabled(
                anyInt(), eq(false), eq(false), any());
    }

    @Test
    public void testTimeout_RetryFailure_WithSatellite() {
        doReturn(true).when(mSatelliteController).isSatelliteEnabled();
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_POWER_OFF);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mMockPhone.getServiceState()).thenReturn(state);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean())).thenReturn(false);
        mListener.setTimeBetweenRetriesMillis(0/* ms */);
        mListener.setMaxNumRetries(2);

        // Wait for the timer to expire and check state manually in onRetryTimeout
        mListener.waitForRadioOn(mMockPhone, mCallback, true, true, 0);
        waitForDelayedHandlerAction(mListener.getHandler(), TIMEOUT_MS /* delay */, TIMEOUT_MS);

        verify(mCallback).onComplete(eq(mListener), eq(false));
        verify(mMockPhone, times(2)).setRadioPower(eq(true), eq(true), eq(true), eq(false));
        verify(mSatelliteController, times(2)).requestSatelliteEnabled(
                anyInt(), eq(false), eq(false), any());
    }

    @Test
    public void testTimeout_OnTimeoutForEmergency() {
        ServiceState state = new ServiceState();
        state.setState(ServiceState.STATE_POWER_OFF);
        when(mMockPhone.getState()).thenReturn(PhoneConstants.State.IDLE);
        when(mMockPhone.getServiceState()).thenReturn(state);
        when(mCallback.isOkToCall(eq(mMockPhone), anyInt(), anyBoolean()))
                .thenReturn(false);
        when(mCallback.onTimeout(eq(mMockPhone), anyInt(), anyBoolean()))
                .thenReturn(true);
        mListener.setTimeBetweenRetriesMillis(0 /* ms */);
        mListener.setMaxNumRetries(1);

        // Wait for the timer to expire and check state manually in onRetryTimeout
        mListener.waitForRadioOn(mMockPhone, mCallback, true, true, 100);
        waitForDelayedHandlerAction(mListener.getHandler(), TIMEOUT_MS /* delay */, TIMEOUT_MS);

        verify(mCallback).onTimeout(eq(mMockPhone), anyInt(), anyBoolean());
        verify(mCallback).onComplete(eq(mListener), eq(true));
    }
}
