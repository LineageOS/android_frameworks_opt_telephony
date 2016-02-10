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

import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.telephony.DisconnectCause;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class GsmCdmaConnectionTest extends TelephonyTest {

    private GsmCdmaConnection connection;

    @Mock
    DriverCall mDC;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        replaceInstance(Handler.class, "mLooper", mCT, Looper.getMainLooper());

        doReturn(PhoneConstants.PHONE_TYPE_GSM).when(mPhone).getPhoneType();
        mCT.mForegroundCall = new GsmCdmaCall(mCT);
        mCT.mBackgroundCall = new GsmCdmaCall(mCT);
        mCT.mRingingCall = new GsmCdmaCall(mCT);
    }

    @After
    public void tearDown() throws Exception {
        connection = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testFormatDialString(){
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
       /* case 1: If PAUSE/WAIT sequence at the end, ignore them */
        String formattedDialStr = connection.formatDialString(
                String.format("+1 (700).555-41NN1234%c", PhoneNumberUtils.PAUSE));
        assertEquals(formattedDialStr, "+1 (700).555-41NN1234");

       /*  case 2: If consecutive PAUSE/WAIT sequence in the middle of the string,
        *  and if there is any WAIT in PAUSE/WAIT sequence, treat them like WAIT.*/
        formattedDialStr = connection.formatDialString("+1 (700).555-41NN,;1234");
        assertEquals(formattedDialStr, "+1 (700).555-41NN;1234");
    }

    @Test @SmallTest
    public void testSanityGSM() {
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
        logd("Testing initial state of GsmCdmaConnection");
        assertEquals(connection.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(connection.getPostDialState(), Connection.PostDialState.NOT_STARTED );
        assertEquals(DisconnectCause.NOT_DISCONNECTED, DisconnectCause.NOT_DISCONNECTED);
        assertEquals(connection.getDisconnectTime(), 0);
        assertEquals(connection.getHoldDurationMillis(), 0);
        assertEquals(connection.getNumberPresentation(), PhoneConstants.PRESENTATION_ALLOWED);
        assertFalse(connection.isMultiparty());
        assertNotNull(connection.getRemainingPostDialString());
        assertEquals(connection.getOrigDialString(), "+1 (700).555-41NN,1234");
    }

    @Test @SmallTest
    public void testSanityCDMA() {
        doReturn(PhoneConstants.PHONE_TYPE_CDMA).when(mPhone).getPhoneType();
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
        logd("Testing initial state of GsmCdmaConnection");
        assertEquals(connection.getState(), GsmCdmaCall.State.IDLE);
        assertEquals(connection.getPostDialState(), Connection.PostDialState.NOT_STARTED);
        assertEquals(DisconnectCause.NOT_DISCONNECTED, DisconnectCause.NOT_DISCONNECTED);
        assertEquals(connection.getDisconnectTime(), 0);
        assertEquals(connection.getHoldDurationMillis(), 0);
        assertEquals(connection.getNumberPresentation(), PhoneConstants.PRESENTATION_ALLOWED);
        assertFalse(connection.isMultiparty());
        assertNotNull(connection.getRemainingPostDialString());
        /* CDMA phone type dont have origDialString */
        assertNull(connection.getOrigDialString());
    }

    @Test @SmallTest
    public void testConnectionStateUpdate() {
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
        logd("Update the connection state from idle to active");
        mDC.state = DriverCall.State.ACTIVE;
        connection.update(mDC);
        assertEquals(connection.getState(), GsmCdmaCall.State.ACTIVE);
        logd("update connection state from active to holding");
        mDC.state = DriverCall.State.HOLDING;
        connection.update(mDC);
        assertEquals(connection.getState(), GsmCdmaCall.State.HOLDING);
        TelephonyTestUtils.waitForMs(50);
        assertTrue(connection.getHoldDurationMillis() >= 50);
    }

    @Test @MediumTest
    public void testCDMAPostDialPause() {
        doReturn(PhoneConstants.PHONE_TYPE_CDMA).when(mPhone).getPhoneType();
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
        logd("Mock connection state from alerting to active ");
        mDC.state = DriverCall.State.ALERTING;
        connection.update(mDC);
        mDC.state = DriverCall.State.ACTIVE;
        connection.update(mDC);
        logd("process post dail sequence with pause");
        assertEquals(connection.getPostDialState(), Connection.PostDialState.PAUSE);
        /* pause for 2000 ms + 50ms margin */
        TelephonyTestUtils.waitForMs(GsmCdmaConnection.PAUSE_DELAY_MILLIS_CDMA + 100);
        assertEquals(connection.getPostDialState(), Connection.PostDialState.COMPLETE);
    }

    @Test @MediumTest
    public void testGSMPostDialPause() {
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
        logd("Mock connection state from alerting to active ");
        mDC.state = DriverCall.State.ALERTING;
        connection.update(mDC);
        mDC.state = DriverCall.State.ACTIVE;
        connection.update(mDC);
        logd("process post dail sequence with pause");
        assertEquals(connection.getPostDialState(), Connection.PostDialState.STARTED);
        /* pause for 2000 ms + 50ms margin */
        TelephonyTestUtils.waitForMs(GsmCdmaConnection.PAUSE_DELAY_MILLIS_GSM + 100);
        assertEquals(connection.getPostDialState(), Connection.PostDialState.COMPLETE);
    }


    @Test @SmallTest
    public void testPostDialWait() {
        doReturn(PhoneConstants.PHONE_TYPE_CDMA).when(mPhone).getPhoneType();
        connection = new GsmCdmaConnection(mPhone,
                String.format("+1 (700).555-41NN%c1234", PhoneNumberUtils.WAIT),mCT,null);
        logd("Mock connection state transition from alerting to active ");
        mDC.state = DriverCall.State.ALERTING;
        connection.update(mDC);
        mDC.state = DriverCall.State.ACTIVE;
        connection.update(mDC);
        logd("Process the post dial sequence with wait ");
        assertEquals(connection.getPostDialState(), Connection.PostDialState.WAIT);
        connection.proceedAfterWaitChar();
        assertEquals(connection.getPostDialState(), Connection.PostDialState.STARTED);
        TelephonyTestUtils.waitForMs(50);
        assertEquals(connection.getPostDialState(), Connection.PostDialState.COMPLETE);
    }

    @Test @SmallTest
    public void testHangUpConnection() {
        connection = new GsmCdmaConnection(mPhone, String.format(
                "+1 (700).555-41NN%c1234", PhoneNumberUtils.PAUSE), mCT, null);
        mDC.state = DriverCall.State.ACTIVE;
        connection.update(mDC);
        logd("Hangup the connection locally");
        connection.onDisconnect(DisconnectCause.LOCAL);
        assertEquals(connection.getState(), GsmCdmaCall.State.DISCONNECTED);
        assertEquals(connection.getDisconnectCause(), DisconnectCause.LOCAL);
        assertTrue(connection.getDisconnectTime() <= System.currentTimeMillis());
    }
}
