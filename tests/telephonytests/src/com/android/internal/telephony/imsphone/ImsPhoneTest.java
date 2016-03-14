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

package com.android.internal.telephony.imsphone;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsCallProfile;
import com.android.ims.ImsStreamMediaProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneInternalInterface;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyChar;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ImsPhoneTest extends TelephonyTest {
    @Mock
    private ImsPhoneCall mForegroundCall;
    @Mock
    private ImsPhoneCall mBackgroundCall;
    @Mock
    private ImsPhoneCall mRingingCall;
    @Mock
    private Handler mTestHandler;
    @Mock
    Connection mConnection;

    private ImsPhone mImsPhoneUT;

    private static final int EVENT_SUPP_SERVICE_NOTIFICATION = 1;
    private static final int EVENT_SUPP_SERVICE_FAILED = 2;

    private class ImsPhoneTestHandler extends HandlerThread {

        private ImsPhoneTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mImsPhoneUT = new ImsPhone(mContext, mNotifier, mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mImsCT.mForegroundCall = mForegroundCall;
        mImsCT.mBackgroundCall = mBackgroundCall;
        mImsCT.mRingingCall = mRingingCall;
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.IDLE).when(mBackgroundCall).getState();
        doReturn(Call.State.IDLE).when(mRingingCall).getState();

        new ImsPhoneTestHandler(TAG).start();
        waitUntilReady();

        replaceInstance(Handler.class, "mLooper", mTestHandler, mImsPhoneUT.getLooper());
        replaceInstance(Phone.class, "mLooper", mPhone, mImsPhoneUT.getLooper());
        mImsPhoneUT.registerForSuppServiceNotification(mTestHandler,
                EVENT_SUPP_SERVICE_NOTIFICATION, null);
        mImsPhoneUT.registerForSuppServiceFailed(mTestHandler,
                EVENT_SUPP_SERVICE_FAILED, null);
    }

    @After
    public void tearDown() throws Exception {
        mImsPhoneUT = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallDeflection() throws Exception {
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("00"));

        // ringing call is not idle
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("0"));
        verify(mImsCT).rejectCall();

        // ringing is idle, background call is not idle
        doReturn(Call.State.IDLE).when(mRingingCall).getState();
        doReturn(Call.State.ACTIVE).when(mBackgroundCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("0"));
        verify(mImsCT).hangup(mBackgroundCall);
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallWaiting() throws Exception {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 2
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("100"));

        // dial string length > 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("10"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.HANGUP,
                ((AsyncResult)messageArgumentCaptor.getValue().obj).result);

        // foreground call is not idle
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("1"));
        verify(mImsCT).hangup(any(ImsPhoneCall.class));

        // foreground call is idle
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("1"));
        verify(mImsCT).switchWaitingOrHoldingAndActive();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallHold() throws Exception {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 2
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("200"));

        // dial string length > 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("20"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.SEPARATE,
                ((AsyncResult) messageArgumentCaptor.getValue().obj).result);

        // ringing call is idle
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT).switchWaitingOrHoldingAndActive();

        // ringing call is not idle
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT).acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandMultiparty() {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("30"));

        // dial string length == 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("3"));
        verify(mImsCT).conference();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallEct() {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("40"));

        // dial string length == 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("4"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.TRANSFER,
                ((AsyncResult) messageArgumentCaptor.getValue().obj).result);
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallCcbs() {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("50"));

        // dial string length == 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("5"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.UNKNOWN,
                ((AsyncResult)messageArgumentCaptor.getValue().obj).result);
    }

    @Test
    @SmallTest
    public void testDispose() {
        // add MMI to verify that dispose removes it
        mImsPhoneUT.sendUssdResponse("1234");
        verify(mImsCT).sendUSSD(eq("1234"), any(Message.class));
        List<?> list = mImsPhoneUT.getPendingMmiCodes();
        assertNotNull(list);
        assertEquals(1, list.size());

        mImsPhoneUT.dispose();
        assertEquals(0, list.size());
        verify(mImsCT).dispose();
        verify(mSST).unregisterForDataRegStateOrRatChanged(mImsPhoneUT);
    }

    @Test
    @SmallTest
    public void testGettersAndPassThroughs() throws Exception {
        assertNotNull(mImsPhoneUT.getServiceState());
        assertEquals(mImsCT, mImsPhoneUT.getCallTracker());

        mImsPhoneUT.acceptCall(0);
        verify(mImsCT).acceptCall(0);

        mImsPhoneUT.rejectCall();
        verify(mImsCT).rejectCall();

        mImsPhoneUT.switchHoldingAndActive();
        verify(mImsCT).switchWaitingOrHoldingAndActive();

        assertEquals(false, mImsPhoneUT.canConference());
        doReturn(true).when(mImsCT).canConference();
        assertEquals(true, mImsPhoneUT.canConference());
        verify(mImsCT, times(2)).canConference();

        assertEquals(false, mImsPhoneUT.canDial());
        doReturn(true).when(mImsCT).canDial();
        assertEquals(true, mImsPhoneUT.canDial());
        verify(mImsCT, times(2)).canDial();

        mImsPhoneUT.conference();
        verify(mImsCT).conference();

        mImsPhoneUT.clearDisconnected();
        verify(mImsCT).clearDisconnected();

        assertEquals(false, mImsPhoneUT.canTransfer());
        doReturn(true).when(mImsCT).canTransfer();
        assertEquals(true, mImsPhoneUT.canTransfer());
        verify(mImsCT, times(2)).canTransfer();

        mImsPhoneUT.explicitCallTransfer();
        verify(mImsCT).explicitCallTransfer();

        assertEquals(mForegroundCall, mImsPhoneUT.getForegroundCall());
        assertEquals(mBackgroundCall, mImsPhoneUT.getBackgroundCall());
        assertEquals(mRingingCall, mImsPhoneUT.getRingingCall());

        mImsPhoneUT.notifyNewRingingConnection(mConnection);
        verify(mPhone).notifyNewRingingConnectionP(mConnection);

        mImsPhoneUT.notifyForVideoCapabilityChanged(true);
        verify(mPhone).notifyForVideoCapabilityChanged(true);
    }

    @Test
    @SmallTest
    public void testSuppServiceNotification() {
        SuppServiceNotification ssn = new SuppServiceNotification();
        mImsPhoneUT.notifySuppSvcNotification(ssn);

        // verify registrants are notified
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler, times(1)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        Message message = messageArgumentCaptor.getValue();
        assertEquals(EVENT_SUPP_SERVICE_NOTIFICATION, message.what);
        assertEquals(ssn, ((AsyncResult)message.obj).result);
        assertEquals(null, ((AsyncResult)message.obj).userObj);
        assertEquals(null, ((AsyncResult) message.obj).exception);

        // verify no notification is received after unregister (verify() still sees only 1
        // notification)
        mImsPhoneUT.unregisterForSuppServiceNotification(mTestHandler);
        mImsPhoneUT.notifySuppSvcNotification(ssn);
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @SmallTest
    public void testDial() throws Exception {
        String dialString = "1234567890";
        int videoState = 0;

        mImsPhoneUT.dial(dialString, videoState);
        verify(mImsCT).dial(dialString, videoState, null);
    }

    @Test
    @SmallTest
    public void testDtmf() {
        // case 1
        mImsCT.mState = PhoneConstants.State.IDLE;
        mImsPhoneUT.sendDtmf('-');
        verify(mImsCT, times(0)).sendDtmf(anyChar(), any(Message.class));

        // case 2
        mImsPhoneUT.sendDtmf('0');
        verify(mImsCT, times(0)).sendDtmf(eq('0'), any(Message.class));

        // case 3
        mImsCT.mState = PhoneConstants.State.OFFHOOK;
        mImsPhoneUT.sendDtmf('-');
        verify(mImsCT, times(0)).sendDtmf(eq('0'), any(Message.class));

        // case 4
        mImsPhoneUT.sendDtmf('0');
        verify(mImsCT, times(1)).sendDtmf(anyChar(), any(Message.class));

        mImsPhoneUT.startDtmf('-');
        verify(mImsCT, times(0)).startDtmf(anyChar());

        mImsPhoneUT.startDtmf('0');
        verify(mImsCT, times(1)).startDtmf('0');

        mImsPhoneUT.stopDtmf();
        verify(mImsCT).stopDtmf();
    }
}
