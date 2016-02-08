/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.icu.impl.Assert;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.ServiceState;
import android.telephony.PhoneNumberUtils;
import java.lang.reflect.Field;

public class CallManagerTest extends TelephonyTest {
    private CallManager mCallManager = CallManager.getInstance();
    @Mock
    GsmCdmaCall mFgCall;
    @Mock
    GsmCdmaCall mBgCall;
    @Mock
    GsmCdmaCall mRingingCall;

    private class CallManagerHandlerThread extends HandlerThread {
        private CallManagerHandlerThread(String name) {
            super(name);
        }
        @Override
        public void onLooperPrepared() {
            /* CallManager is a static object with private constructor,no need call constructor */
            CallManager.getInstance().registerPhone(mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        Field field = CallManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, mCallManager);
        /* Mock Phone and Call, initially all calls are idle */
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();
        doReturn(mServiceState).when(mPhone).getServiceState();
        doReturn(mBgCall).when(mPhone).getBackgroundCall();
        doReturn(mFgCall).when(mPhone).getForegroundCall();
        doReturn(mRingingCall).when(mPhone).getRingingCall();
        doReturn(mPhone).when(mBgCall).getPhone();
        doReturn(mPhone).when(mFgCall).getPhone();
        doReturn(mPhone).when(mRingingCall).getPhone();
        doReturn(Call.State.IDLE).when(mBgCall).getState();
        doReturn(Call.State.IDLE).when(mFgCall).getState();
        doReturn(Call.State.IDLE).when(mRingingCall).getState();
        doReturn(true).when(mBgCall).isIdle();
        doReturn(true).when(mFgCall).isIdle();
        doReturn(true).when(mRingingCall).isIdle();

        new CallManagerHandlerThread(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        CallManager.getInstance().unregisterPhone(mPhone);
        super.tearDown();
    }

    @SmallTest @Test
    public void testSanity() {
        assertEquals(CallManager.getInstance().getDefaultPhone(), mPhone);
        assertFalse(CallManager.getInstance().hasActiveBgCall());
        assertFalse(CallManager.getInstance().hasActiveRingingCall());
        assertFalse(CallManager.getInstance().hasActiveFgCall());
        /* return the default phone if there is no any active call */
        assertEquals(CallManager.getInstance().getRingingPhone(), mPhone);
        assertEquals(CallManager.getInstance().getBgPhone(), mPhone);
        assertEquals(CallManager.getInstance().getFgPhone(), mPhone);
    }

    @SmallTest @Test
    public void testBasicDial() {
        try {
            //verify can dial and dial function of the phone is being triggered
            CallManager.getInstance().dial(mPhone,
                    PhoneNumberUtils.stripSeparators("+17005554141"), 0);
            ArgumentCaptor<String> mCaptorString = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> mCaptorInt = ArgumentCaptor.forClass(Integer.class);
            verify(mPhone, times(1)).dial(mCaptorString.capture(), mCaptorInt.capture());
            assertEquals(mCaptorString.getValue(),
                    PhoneNumberUtils.stripSeparators("+17005554141"));
            assertEquals(mCaptorInt.getValue().intValue(), 0);

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }

    }

    @SmallTest @Test
    public void testBasicAcceptCall() {
        try {
            CallManager.getInstance().acceptCall(mRingingCall);
            verify(mPhone, times(1)).acceptCall(anyInt());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }
    }

    @SmallTest @Test
    public void testBasicRejectCall() {
        try {
            //verify can dial and dial function of the phone is being triggered
            CallManager.getInstance().rejectCall(mRingingCall);
            verify(mPhone, times(1)).rejectCall();
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }
    }

    @SmallTest @Test
    public void testSendDtmf() {
        CallManager.getInstance().sendDtmf('a');
        verify(mPhone, times(0)).sendDtmf(eq('a'));
        doReturn(false).when(mFgCall).isIdle();
        assertEquals(CallManager.getInstance().getActiveFgCall(), mFgCall);
        CallManager.getInstance().sendDtmf('a');
        verify(mPhone, times(1)).sendDtmf(eq('a'));
    }

    @SmallTest @Test
    public void testSwitchHoldingAndActive() {
        try {
            /* case 1: only active call */
            doReturn(false).when(mFgCall).isIdle();
            CallManager.getInstance().switchHoldingAndActive(null);
            verify(mPhone, times(1)).switchHoldingAndActive();
            /* case 2: no active call but only held call, aka, unhold */
            doReturn(true).when(mFgCall).isIdle();
            CallManager.getInstance().switchHoldingAndActive(mBgCall);
            verify(mPhone, times(2)).switchHoldingAndActive();
            /* case 3: both active and held calls from same phone, aka, swap */
            doReturn(false).when(mFgCall).isIdle();
            CallManager.getInstance().switchHoldingAndActive(mBgCall);
            verify(mPhone, times(3)).switchHoldingAndActive();
            GsmCdmaPhone mPhoneHold = Mockito.mock(GsmCdmaPhone.class);
            /* case 4: active and held calls from different phones, aka, phone swap */
            doReturn(mPhoneHold).when(mBgCall).getPhone();
            CallManager.getInstance().switchHoldingAndActive(mBgCall);
            verify(mPhone, times(4)).switchHoldingAndActive();
            verify(mPhoneHold, times(1)).switchHoldingAndActive();
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }
    }

    @SmallTest @Test
    public void testHangupForegroundResumeBackground() {
        try {
            CallManager.getInstance().hangupForegroundResumeBackground(mBgCall);
            /* no active fgCall */
            verify(mPhone, times(0)).switchHoldingAndActive();
            verify(mFgCall, times(0)).hangup();

            /* have active foreground call, get hanged up */
            doReturn(false).when(mFgCall).isIdle();
            CallManager.getInstance().hangupForegroundResumeBackground(mBgCall);
            verify(mFgCall, times(1)).hangup();
            verify(mPhone, times(0)).switchHoldingAndActive();

            /* mock bgcall and fgcall from different phone */
            GsmCdmaPhone mPhoneHold = Mockito.mock(GsmCdmaPhone.class);
            doReturn(mPhoneHold).when(mBgCall).getPhone();
            CallManager.getInstance().hangupForegroundResumeBackground(mBgCall);
            verify(mFgCall, times(2)).hangup();
            /* always hangup fgcall and both phone trigger swap */
            verify(mPhoneHold, times(1)).switchHoldingAndActive();
            verify(mPhone, times(1)).switchHoldingAndActive();

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }
    }

    @SmallTest @Test
    public void testFgCallActiveDial() {
        /* set Fg/Bg Call state to active, verify CallManager Logical */
        doReturn(false).when(mFgCall).isIdle();
        doReturn(false).when(mBgCall).isIdle();
        assertTrue(CallManager.getInstance().hasActiveFgCall());
        assertTrue(CallManager.getInstance().hasActiveBgCall());
        assertTrue(CallManager.getInstance().hasActiveFgCall(mPhone.getSubId()));
        assertFalse(CallManager.getInstance().hasDisconnectedFgCall());
        /* try dial with non-idle foreground call and background call */
        try {
            CallManager.getInstance().dial(mPhone,
                    PhoneNumberUtils.stripSeparators("+17005554141"), 0);
            ArgumentCaptor<String> mCaptorString = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> mCaptorInt = ArgumentCaptor.forClass(Integer.class);

            verify(mPhone, times(1)).dial(mCaptorString.capture(), mCaptorInt.capture());
            assertEquals(mCaptorString.getValue(),
                    PhoneNumberUtils.stripSeparators("+17005554141"));
            assertEquals(mCaptorInt.getValue().intValue(), 0);

        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }
    }

    @Test @SmallTest
    public void testRegisterEvent() {
        try {
            Field field = CallManager.class.getDeclaredField("EVENT_CALL_WAITING");
            field.setAccessible(true);
            int mEvent = (Integer) field.get(CallManager.getInstance());
            verify(mPhone, times(1)).registerForCallWaiting(isA(Handler.class),
                    eq(mEvent), isNull());

            field = CallManager.class.getDeclaredField("EVENT_PRECISE_CALL_STATE_CHANGED");
            field.setAccessible(true);
            mEvent = (Integer) field.get(CallManager.getInstance());
            verify(mPhone, times(1)).registerForPreciseCallStateChanged(isA(Handler.class),
                    eq(mEvent), isA(Object.class));

            field = CallManager.class.getDeclaredField("EVENT_RINGBACK_TONE");
            field.setAccessible(true);
            mEvent = (Integer) field.get(CallManager.getInstance());
            verify(mPhone, times(1)).registerForRingbackTone(isA(Handler.class),
                    eq(mEvent), isA(Object.class));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.toString());
        }
    }
}
