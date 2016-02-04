/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.os.HandlerThread;
import android.os.SystemProperties;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.test.SimulatedCommandsVerifier;
import android.os.Message;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import android.os.Handler;
import android.util.SparseArray;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;

public class GsmCdmaCallTrackerTest {
    private static final String TAG = "GsmCdmaCallTrackerTest";
    private static final int VOICE_CALL_STARTED_EVENT = 0;
    private static final int VOICE_CALL_ENDED_EVENT = 1;
    private Object mLock = new Object();
    private boolean mReady = false;
    private SimulatedCommands mCi;
    private ContextFixture mcontextFixture;

    String mDialString = PhoneNumberUtils.stripSeparators("+17005554141");
    /* Handler class initiated at the HandlerThread */
    private GsmCdmaCallTracker mCT;
    @Mock
    GsmCdmaPhone mPhone;
    @Mock
    GsmCdmaCall mCall;
    @Mock
    ServiceState mServiceState;
    @Mock
    private SimulatedCommandsVerifier mSimulatedCommandsVerifier;
    @Mock
    private Handler mHandler;
    @Mock
    private TelephonyEventLog mTelephonyEventLog;

    private class GsmCdmaCTHandlerThread extends HandlerThread {

        private GsmCdmaCTHandlerThread(String name) {
            super(name);
        }
        @Override
        public void onLooperPrepared() {
            synchronized (mLock) {
                mCT =  TelephonyComponentFactory.getInstance().makeGsmCdmaCallTracker(mPhone);
                mReady = true;
            }
        }
    }

    private void waitUntilReady() {
        while(true) {
            synchronized (mLock) {
                if (mReady) {
                    break;
                }
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mcontextFixture = new ContextFixture();
        mCi = new SimulatedCommands();
        mCi.setRadioPower(true, null);
        mPhone.mCi = this.mCi;
        doReturn(mServiceState).when(mPhone).getServiceState();
        doReturn(mcontextFixture.getTestDouble()).when(mPhone).getContext();
        doReturn(PhoneConstants.PHONE_TYPE_GSM).when(mPhone).getPhoneType();
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mcontextFixture.putStringArrayResource(com.android.internal.R.array.dial_string_replace,
                new String[]{});
        Field field = SimulatedCommandsVerifier.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSimulatedCommandsVerifier);
        mReady = false;

        field = TelephonyEventLog.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        SparseArray<TelephonyEventLog> mTelephonyEventArr = new SparseArray<TelephonyEventLog>();
        mTelephonyEventArr.put(mPhone.getPhoneId(), mTelephonyEventLog);
        field.set(null,mTelephonyEventArr);

        new GsmCdmaCTHandlerThread(TAG).start();

        waitUntilReady();
        logd("GsmCdmaCallTracker initiated");
        logd("Waiting for Power on");
        /* Make sure radio state is power on before dial.
         * When radio state changed from off to on, CallTracker
         * will poll result from RIL. Avoid dialing triggered at the same*/
        TelephonyTestUtils.waitForMs(100);
    }

    @After
    public void tearDown() throws Exception {
        mCT = null;
    }

    @Test
    @SmallTest
    public void testMOCallDial() {
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();
        assertEquals(mCT.getState(), PhoneConstants.State.IDLE);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 0);
        /* debug */
        assertTrue(mPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF);
        assertFalse(mCT.mRingingCall.isRinging());
        assertFalse(mCT.mForegroundCall.getState().isAlive());
        assertFalse(!SystemProperties.get(
                TelephonyProperties.PROPERTY_DISABLE_CALL, "false").equals("true"));

        try {
            mCT.dial(mDialString);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown"+ex.getMessage());
        }

        assertEquals(mCT.getState(), PhoneConstants.State.OFFHOOK);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.DIALING);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 1);
        /* verify the command is sent out to RIL */
        verify(mSimulatedCommandsVerifier).dial(eq(PhoneNumberUtils.
                        extractNetworkPortionAlt(mDialString)), anyInt(),
                eq((UUSInfo) null),
                isA(Message.class));
    }

    @Test
    @SmallTest
    public void testMOCallPickUp() {
        testMOCallDial();
        logd("Waiting for POLL CALL response from RIL");
        TelephonyTestUtils.waitForMs(50);
        logd("Pick Up MO call, expecting call state change event ");
        mCi.progressConnectingToActive();
        TelephonyTestUtils.waitForMs(100);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.ACTIVE);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.IDLE);
    }

    @Test
    @SmallTest
    public void testMOCallHangup() {
        testMOCallDial();
        logd("Waiting for POLL CALL response from RIL ");
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.DIALING);
        assertEquals(mCT.getState(), PhoneConstants.State.OFFHOOK);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 1);
        logd("Hang up MO call after MO call established ");
        try {
            mCT.hangup(mCT.mForegroundCall);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.DISCONNECTING);
        /* request send to RIL still in disconnecting state */
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mForegroundCall.getConnections().size(),0);
        assertEquals(mCT.getState(), PhoneConstants.State.IDLE);
    }

    @Test
    @SmallTest
    public void testMOCallDialPickUpHangup() {
        testMOCallPickUp();
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.ACTIVE);
        assertEquals(mCT.getState(), PhoneConstants.State.OFFHOOK);
        assertEquals(mCT.mForegroundCall.getConnections().size(),1);
         /* get the reference of the connection before reject */
        Connection mConnection = mCT.mForegroundCall.getConnections().get(0);
        assertEquals(mConnection.getDisconnectCause(), DisconnectCause.NOT_DISCONNECTED);
        logd("hang up MO call after pickup");
        try {
            mCT.hangup(mCT.mForegroundCall);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.DISCONNECTING);
        /* request send to RIL still in disconnecting state */
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 0);
        assertEquals(mCT.getState(), PhoneConstants.State.IDLE);
        assertEquals(mConnection.getDisconnectCause(), DisconnectCause.LOCAL);

    }

    @Test
    @SmallTest
    public void testMOCallPendingHangUp() {
        testMOCallDial();
        logd("MO call hangup before established[ getting result from RIL ]");
        /* poll call result from RIL, find that there is a pendingMO call,
         * Didn't do anything for hangup, clear during handle poll result */
        try {
            mCT.hangup(mCT.mForegroundCall);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mForegroundCall.getConnections().size(),0);
        assertEquals(mCT.getState(), PhoneConstants.State.IDLE);
    }

    @Test
    @SmallTest
    public void testMOCallSwitch() {
        testMOCallPickUp();
        logd("MO call picked up, initiating a new MO call");
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.ACTIVE);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 1);
        assertEquals(mCT.mBackgroundCall.getConnections().size(), 0);

        String mDialString = PhoneNumberUtils.stripSeparators("+17005554142");
        try {
            mCT.dial(mDialString);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        TelephonyTestUtils.waitForMs(100);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.DIALING);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.HOLDING);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 1);
        assertEquals(mCT.mBackgroundCall.getConnections().size(), 1);

    }

    @Test
    @SmallTest
    public void testMTCallRinging() {
        /* Mock there is a MT call mRinging call and try to accept this MT call */
        /* if we got a active state followed by another MT call-> move to background call */
        assertEquals(mCT.getState(), PhoneConstants.State.IDLE);
        assertEquals(mCT.mRingingCall.getConnections().size(), 0);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        String mDialString = PhoneNumberUtils.stripSeparators("+17005554141");
        logd("MT call Ringing");
        mCi.triggerRing(mDialString);
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.getState(), PhoneConstants.State.RINGING);
        assertEquals(mCT.mRingingCall.getConnections().size(), 1);
    }

    @Test
    @SmallTest
    public void testMTCallAccept() {
        testMTCallRinging();
        assertEquals(mCT.mForegroundCall.getConnections().size(),0);
        logd("accept the MT call");
        try{
            mCT.acceptCall();
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        verify(mSimulatedCommandsVerifier).acceptCall(isA(Message.class));
        /* send to the RIL */
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.getState(), PhoneConstants.State.OFFHOOK);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.ACTIVE);
        assertEquals(mCT.mForegroundCall.getConnections().size(),1);
        assertEquals(mCT.mRingingCall.getConnections().size(),0);
    }

    @Test
    @SmallTest
    public void testMTCallReject() {
        testMTCallRinging();
        logd("MT call ringing and rejected ");
        /* get the reference of the connection before reject */
        Connection mConnection = mCT.mRingingCall.getConnections().get(0);
        assertNotNull(mConnection);
        assertEquals(mConnection.getDisconnectCause(), DisconnectCause.NOT_DISCONNECTED);
        try {
            mCT.rejectCall();
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        TelephonyTestUtils.waitForMs(50);
        assertEquals(mCT.getState(), PhoneConstants.State.IDLE);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mForegroundCall.getConnections().size(), 0);
        /* ? why rejectCall didnt -> hang up locally to set the cause to LOCAL? */
        assertEquals(mConnection.getDisconnectCause(), DisconnectCause.INCOMING_MISSED);

    }

    @Test
    @SmallTest
    public void testMOCallSwitchHangupForeGround() {
        testMOCallSwitch();
        logd("Hang up the foreground MO call while dialing ");
        try {
            mCT.hangup(mCT.mForegroundCall);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        TelephonyTestUtils.waitForMs(100);
        logd(" Foreground Call is IDLE and BackGround Call is still HOLDING ");
        /* if we want to hang up foreground call which is alerting state, hangup all */
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.HOLDING);
    }

    @Test
    @SmallTest
    public void testMOCallPickUpHangUpResumeBackGround() {
        testMOCallSwitch();
        logd("Pick up the new MO Call");
        try{
            mCi.progressConnectingToActive();
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }

        TelephonyTestUtils.waitForMs(100);
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.ACTIVE);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.HOLDING);

        logd("Hang up the new MO Call");
        try {
            mCT.hangup(mCT.mForegroundCall);
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }

        TelephonyTestUtils.waitForMs(100);
        logd(" BackGround Call switch to ForeGround Call ");
        assertEquals(mCT.mForegroundCall.getState(), GsmCdmaCall.State.ACTIVE);
        assertEquals(mCT.mBackgroundCall.getState(), GsmCdmaCall.State.IDLE);
    }

    @Test @SmallTest
    public void testVoiceCallStartListener(){
        logd("register for voice call started event");
        mCT.registerForVoiceCallStarted(mHandler, VOICE_CALL_STARTED_EVENT, null);
        logd("voice call started");
        testMOCallPickUp();
        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long>    mCaptorLong = ArgumentCaptor.forClass(Long.class);
        verify(mHandler,times(1)).sendMessageAtTime(mCaptorMessage.capture(), mCaptorLong.capture());
        assertEquals(VOICE_CALL_STARTED_EVENT, mCaptorMessage.getValue().what);

    }

    @Test @SmallTest
    public void testVoiceCallEndedListener(){
        logd("register for voice call ended event");
        mCT.registerForVoiceCallEnded(mHandler, VOICE_CALL_ENDED_EVENT, null);
        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long>    mCaptorLong = ArgumentCaptor.forClass(Long.class);
        testMOCallHangup();
        verify(mHandler,times(1)).sendMessageAtTime(mCaptorMessage.capture(), mCaptorLong.capture());
        assertEquals(VOICE_CALL_ENDED_EVENT, mCaptorMessage.getValue().what);
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}

