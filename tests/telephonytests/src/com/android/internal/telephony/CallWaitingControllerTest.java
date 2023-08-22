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
package com.android.internal.telephony;

import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_FIRST_CHANGE;
import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_FIRST_POWER_UP;
import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_IMS_ONLY;
import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_NONE;
import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_USER_CHANGE;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_TERMINAL_BASED_CALL_WAITING_DEFAULT_ENABLED_BOOL;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CW;

import static com.android.internal.telephony.CallWaitingController.KEY_CS_SYNC;
import static com.android.internal.telephony.CallWaitingController.KEY_STATE;
import static com.android.internal.telephony.CallWaitingController.KEY_SUB_ID;
import static com.android.internal.telephony.CallWaitingController.PREFERENCE_TBCW;
import static com.android.internal.telephony.CallWaitingController.TERMINAL_BASED_ACTIVATED;
import static com.android.internal.telephony.CallWaitingController.TERMINAL_BASED_NOT_ACTIVATED;
import static com.android.internal.telephony.CallWaitingController.TERMINAL_BASED_NOT_SUPPORTED;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_DATA;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_NONE;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CallWaitingControllerTest extends TelephonyTest {
    private static final int FAKE_SUB_ID = 1;

    private static final int GET_DONE = 1;

    private CallWaitingController mCWC;
    private GetTestHandler mHandler;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        mSimulatedCommands.setRadioPower(true, null);
        mPhone.mCi = this.mSimulatedCommands;
        doReturn(FAKE_SUB_ID).when(mPhone).getSubId();

        mCWC = new CallWaitingController(mPhone);
        logd("CallWaitingController initiated, waiting for Power on");
        /* Make sure radio state is power on before dial.
         * When radio state changed from off to on, CallTracker
         * will poll result from RIL. Avoid dialing triggered at the same*/
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mCWC = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSetTerminalBasedCallWaitingSupported() {
        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_NONE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);

        mCWC.setTerminalBasedCallWaitingSupported(false);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_SUPPORTED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_SUPPORTED);
    }

    @Test
    @SmallTest
    public void testInitialize() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        setPreference(mPhone.getPhoneId(), FAKE_SUB_ID,
                TERMINAL_BASED_ACTIVATED, CALL_WAITING_SYNC_NONE);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_NONE, true);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt(), any());
        mCWC.setTerminalBasedCallWaitingSupported(true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);

        mCWC.setTerminalBasedCallWaitingSupported(false);
        setPreference(mPhone.getPhoneId(), FAKE_SUB_ID,
                TERMINAL_BASED_NOT_ACTIVATED, CALL_WAITING_SYNC_NONE);
        mCWC.setTerminalBasedCallWaitingSupported(true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);

        mCWC.setTerminalBasedCallWaitingSupported(false);
        bundle = getConfigBundle(false, CALL_WAITING_SYNC_NONE, false);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt(), any());
        mCWC.setTerminalBasedCallWaitingSupported(true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_SUPPORTED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_SUPPORTED);
    }

    @Test
    @SmallTest
    public void testCarrierConfigChanged() {
        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_NONE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);

        bundle = getConfigBundle(false, CALL_WAITING_SYNC_NONE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, false);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_SUPPORTED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_SUPPORTED);
    }


    private static class GetTestHandler extends Handler {
        public int[] resp;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GET_DONE:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    resp = (int[]) ar.result;
                    break;
                default:
            }
        }

        public void reset() {
            resp = null;
        }
    }

    @Test
    @SmallTest
    public void testGetCallWaitingSyncNone() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        assertFalse(mCWC.getCallWaiting(null));

        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_NONE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);

        mHandler = new GetTestHandler();

        assertTrue(mCWC.setCallWaiting(true, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_VOICE, mHandler.resp[1]);

        mHandler.reset();

        assertTrue(mCWC.setCallWaiting(false, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_NOT_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_NONE, mHandler.resp[1]);
    }

    @Test
    @SmallTest
    public void testSetCallWaitingSyncNone() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        assertFalse(mCWC.setCallWaiting(true, SERVICE_CLASS_VOICE, null));

        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_NONE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);

        assertTrue(mCWC.setCallWaiting(true, SERVICE_CLASS_VOICE, null));
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_ACTIVATED);

        assertTrue(mCWC.setCallWaiting(false, SERVICE_CLASS_VOICE | SERVICE_CLASS_DATA, null));
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);

        assertFalse(mCWC.setCallWaiting(true, SERVICE_CLASS_DATA, null));
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);

        assertFalse(mCWC.setCallWaiting(true, SERVICE_CLASS_NONE, null));
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);
    }

    @Test
    @SmallTest
    public void testSyncUserChange() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        setPreference(mPhone.getPhoneId(), FAKE_SUB_ID,
                TERMINAL_BASED_ACTIVATED, CALL_WAITING_SYNC_USER_CHANGE);
        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_USER_CHANGE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_ACTIVATED);

        mHandler = new GetTestHandler();

        mSimulatedCommands.setCallWaiting(false, SERVICE_CLASS_VOICE, null);

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_NOT_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_NONE, mHandler.resp[1]);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);

        mHandler.reset();

        mSimulatedCommands.setCallWaiting(true, SERVICE_CLASS_VOICE, null);

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_VOICE, mHandler.resp[1]);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_ACTIVATED);

        mHandler.reset();

        assertTrue(mCWC.setCallWaiting(false, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);
    }

    @Test
    @SmallTest
    public void testSyncFirstPowerUp() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        setPreference(mPhone.getPhoneId(), FAKE_SUB_ID,
                TERMINAL_BASED_NOT_ACTIVATED, CALL_WAITING_SYNC_FIRST_POWER_UP);
        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_FIRST_POWER_UP, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);
        assertFalse(mCWC.getSyncState());

        mCWC.notifyRegisteredToNetwork();
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getSyncState());
    }

    @Test
    @SmallTest
    public void testSyncFirstChange() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        setPreference(mPhone.getPhoneId(), FAKE_SUB_ID,
                TERMINAL_BASED_NOT_ACTIVATED, CALL_WAITING_SYNC_FIRST_CHANGE);
        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_FIRST_CHANGE, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);
        mCWC.setImsRegistrationState(false);

        assertFalse(mCWC.getSyncState());

        mSimulatedCommands.setCallWaiting(false, SERVICE_CLASS_VOICE, null);
        mCWC.getCallWaiting(null);
        mTestableLooper.processAllMessages();

        assertFalse(mCWC.getSyncState());

        mSimulatedCommands.setCallWaiting(true, SERVICE_CLASS_VOICE, null);
        mCWC.getCallWaiting(null);
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getSyncState());

        assertTrue(mCWC.setCallWaiting(true, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mSimulatedCommands.mCallWaitActivated);

        assertTrue(mCWC.setCallWaiting(false, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        // Local setting changed, but no change in CS network.
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mSimulatedCommands.mCallWaitActivated);
    }

    @Test
    @SmallTest
    public void testSyncImsOnly() {
        mCWC.setTerminalBasedCallWaitingSupported(false);
        setPreference(mPhone.getPhoneId(), FAKE_SUB_ID,
                TERMINAL_BASED_ACTIVATED, CALL_WAITING_SYNC_IMS_ONLY);
        mCWC.setTerminalBasedCallWaitingSupported(true);
        PersistableBundle bundle = getConfigBundle(true, CALL_WAITING_SYNC_IMS_ONLY, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(bundle);
        mCWC.updateCarrierConfig(FAKE_SUB_ID, true);

        mSimulatedCommands.setCallWaiting(false, SERVICE_CLASS_VOICE, null);

        // IMS is registered
        mCWC.setImsRegistrationState(true);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_ACTIVATED);

        mHandler = new GetTestHandler();

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        // result carries the service state from IMS service
        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_VOICE, mHandler.resp[1]);

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_ACTIVATED);

        mHandler.reset();

        // IMS is not registered
        mCWC.setImsRegistrationState(false);

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        // result carries the service state from CS
        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_NOT_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_NONE, mHandler.resp[1]);

        // service state not synchronized between CS and IMS
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_SUPPORTED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_ACTIVATED);

        mHandler.reset();

        // IMS is registered
        mCWC.setImsRegistrationState(true);

        assertTrue(mCWC.setCallWaiting(false, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);

        // IMS is not registered
        mCWC.setImsRegistrationState(false);

        assertTrue(mCWC.setCallWaiting(true, SERVICE_CLASS_VOICE, null));
        mTestableLooper.processAllMessages();

        assertTrue(mCWC.getCallWaiting(mHandler.obtainMessage(GET_DONE)));
        mTestableLooper.processAllMessages();

        // result carries the service state from CS
        assertNotNull(mHandler.resp);
        assertEquals(2, mHandler.resp.length);
        assertEquals(TERMINAL_BASED_ACTIVATED, mHandler.resp[0]);
        assertEquals(SERVICE_CLASS_VOICE, mHandler.resp[1]);

        // service state not synchronized between CS and IMS
        assertTrue(mCWC.getTerminalBasedCallWaitingState(false) == TERMINAL_BASED_NOT_ACTIVATED);
        assertTrue(mCWC.getTerminalBasedCallWaitingState(true) == TERMINAL_BASED_NOT_SUPPORTED);
        assertTrue(retrieveStatePreference(mPhone.getSubId()) == TERMINAL_BASED_NOT_ACTIVATED);
    }

    private PersistableBundle getConfigBundle(boolean provisioned,
            int preference, boolean defaultState) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY,
                provisioned ? new int[] { SUPPLEMENTARY_SERVICE_CW } : new int[] { });
        bundle.putInt(KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT, preference);
        bundle.putBoolean(KEY_TERMINAL_BASED_CALL_WAITING_DEFAULT_ENABLED_BOOL, defaultState);
        return bundle;
    }

    private int retrieveStatePreference(int subId) {
        SharedPreferences sp =
                mContext.getSharedPreferences(PREFERENCE_TBCW, Context.MODE_PRIVATE);
        return sp.getInt(KEY_STATE + subId, TERMINAL_BASED_NOT_SUPPORTED);
    }

    private void setPreference(int phoneId, int subId, int state, int syncPreference) {
        SharedPreferences sp =
                mContext.getSharedPreferences(PREFERENCE_TBCW, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(KEY_SUB_ID + phoneId, subId);
        editor.putInt(KEY_STATE + subId, state);
        editor.putInt(KEY_CS_SYNC + phoneId, syncPreference);
        editor.apply();
    }
}
