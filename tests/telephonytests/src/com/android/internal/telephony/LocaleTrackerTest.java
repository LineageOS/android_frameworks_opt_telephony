/*
 * Copyright 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class LocaleTrackerTest extends TelephonyTest {

    private static final String US_MCC = "310";
    private static final String LIECHTENSTEIN_MCC = "295";
    private static final String TEST_CELL_MCC = "001";

    private static final String FAKE_MNC = "123";

    private static final String COUNTRY_CODE_UNAVAILABLE = "";
    private static final String US_COUNTRY_CODE = "us";
    private static final String LIECHTENSTEIN_COUNTRY_CODE = "li";

    private LocaleTracker mLocaleTracker;

    private CellInfoGsm mCellInfo;

    @Before
    public void setUp() throws Exception {
        logd("LocaleTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        mLocaleTracker = new LocaleTracker(mPhone, mNitzStateMachine, Looper.myLooper());

        // This is a workaround to bypass setting system properties, which causes access violation.
        doReturn(-1).when(mPhone).getPhoneId();

        mCellInfo = new CellInfoGsm();
        mCellInfo.setCellIdentity(new CellIdentityGsm(
                    CellInfo.UNAVAILABLE, CellInfo.UNAVAILABLE,
                    CellInfo.UNAVAILABLE, CellInfo.UNAVAILABLE,
                    US_MCC, FAKE_MNC, null, null, Collections.emptyList()));
        doAnswer(invocation -> {
            Message m = invocation.getArgument(1);
            AsyncResult.forMessage(m, Arrays.asList(mCellInfo), null);
            m.sendToTarget();
            return null; }).when(mPhone).requestCellInfoUpdate(any(), any());

        doReturn(true).when(mPhone).isRadioOn();
        processAllMessages();
        logd("LocaleTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void sendServiceState(int state) {
        ServiceState ss = new ServiceState();
        ss.setState(state);
        AsyncResult ar = new AsyncResult(null, ss, null);
        mLocaleTracker.sendMessage(
                mLocaleTracker.obtainMessage(2 /*SERVICE_STATE_CHANGED*/, ar));
        processAllMessages();
    }

    private void sendGsmCellInfo() {
        // send an unsol cell info
        mLocaleTracker
                .obtainMessage(4 /*UNSOL_CELL_INFO*/,
                        new AsyncResult(null, Arrays.asList(mCellInfo), null))
                .sendToTarget();
        processAllMessages();
    }

    private void sendOperatorLost() {
        mLocaleTracker.sendMessage(mLocaleTracker.obtainMessage(6 /* EVENT_OPERATOR_LOST */));
        processAllMessages();
    }

    private void verifyCountryCodeNotified(String[] countryCodes) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(countryCodes.length)).sendBroadcast(intentArgumentCaptor.capture());
        List<Intent> intents = intentArgumentCaptor.getAllValues();

        for (int i = 0; i < countryCodes.length; i++) {
            assertEquals(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED,
                    intents.get(i).getAction());
            assertEquals(countryCodes[i], intents.get(i).getStringExtra(
                    TelephonyManager.EXTRA_NETWORK_COUNTRY));
        }
    }

    @Test
    @SmallTest
    public void testUpdateOperatorNumericSync() throws Exception {
        doReturn(false).when(mPhone).isRadioOn();
        mLocaleTracker.updateOperatorNumeric(US_MCC + FAKE_MNC);
        // Because the service state is in APM, the country ISO should be set empty.
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
    }

    @Test
    @SmallTest
    public void testNoSim() throws Exception {
        // Set the state as STATE_OUT_OF_SERVICE. This will trigger an country change to US.
        sendServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE});

        // updateOperatorNumeric("") will not trigger an instantaneous country change
        mLocaleTracker.updateOperatorNumeric("");
        sendGsmCellInfo();
        sendServiceState(ServiceState.STATE_EMERGENCY_ONLY);
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE});
        assertTrue(mLocaleTracker.isTracking());
    }

    @Test
    @SmallTest
    public void testBootupInAirplaneModeOn() throws Exception {
        mLocaleTracker.updateOperatorNumeric("");
        doReturn(false).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_POWER_OFF);
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE});
        assertFalse(mLocaleTracker.isTracking());
    }

    @Test
    @SmallTest
    public void testToggleAirplaneModeOn() throws Exception {
        doReturn(true).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_IN_SERVICE);
        mLocaleTracker.updateOperatorNumeric(US_MCC + FAKE_MNC);
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getLastKnownCountryIso());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE});
        assertFalse(mLocaleTracker.isTracking());

        // updateOperatorNumeric("") will not trigger an instantaneous country change
        mLocaleTracker.updateOperatorNumeric("");
        processAllMessages();
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getLastKnownCountryIso());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE});
        doReturn(false).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_POWER_OFF);
        assertFalse(mLocaleTracker.isTracking());

        // updateOperatorNumeric("") will trigger a country change in APM
        mLocaleTracker.updateOperatorNumeric("");
        processAllMessages();
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getLastKnownCountryIso());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE,
                COUNTRY_CODE_UNAVAILABLE});
    }

    @Test
    @SmallTest
    public void testToggleAirplaneModeOff() throws Exception {
        doReturn(false).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_POWER_OFF);
        mLocaleTracker.updateOperatorNumeric("");
        processAllMessages();
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE});
        assertFalse(mLocaleTracker.isTracking());

        doReturn(true).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        processAllMessages();
        assertTrue(mLocaleTracker.isTracking());
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
    }

    @Test
    @SmallTest
    public void testToggleAirplaneModeOosPlmn() throws Exception {
        doReturn(false).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_POWER_OFF);
        mLocaleTracker.updateOperatorNumeric("");
        processAllMessages();
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE});
        assertFalse(mLocaleTracker.isTracking());

        // Override the setUp() function and return an empty list for CellInfo
        doAnswer(invocation -> {
            Message m = invocation.getArgument(1);
            AsyncResult.forMessage(m, Collections.emptyList(), null);
            m.sendToTarget();
            return null; }).when(mPhone).requestCellInfoUpdate(any(), any());

        doReturn(true).when(mPhone).isRadioOn();
        sendServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        processAllMessages();
        assertTrue(mLocaleTracker.isTracking());
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());

        mLocaleTracker.updateOperatorNumeric(US_MCC + FAKE_MNC);
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE});

        mLocaleTracker.updateOperatorNumeric("");
        processAllMessages();
        assertEquals(US_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE});

        mLocaleTracker.updateOperatorNumeric(LIECHTENSTEIN_MCC + FAKE_MNC);
        processAllMessages();
        assertEquals(LIECHTENSTEIN_COUNTRY_CODE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{
                COUNTRY_CODE_UNAVAILABLE, US_COUNTRY_CODE, LIECHTENSTEIN_COUNTRY_CODE});
    }

    @Test
    @SmallTest
    public void testToggleAirplaneModeNoCellInfo() throws Exception {
        sendServiceState(ServiceState.STATE_POWER_OFF);
        mLocaleTracker.updateOperatorNumeric("");
        processAllMessages();
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
        verifyCountryCodeNotified(new String[]{COUNTRY_CODE_UNAVAILABLE});
        assertFalse(mLocaleTracker.isTracking());

        // Override the setUp() function and return an empty list for CellInfo
        doAnswer(invocation -> {
            Message m = invocation.getArgument(1);
            AsyncResult.forMessage(m, Collections.emptyList(), null);
            m.sendToTarget();
            return null; }).when(mPhone).requestCellInfoUpdate(any(), any());

        sendServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        processAllMessages();
        assertTrue(mLocaleTracker.isTracking());
        assertEquals(COUNTRY_CODE_UNAVAILABLE, mLocaleTracker.getCurrentCountry());
    }


    @Test
    @SmallTest
    public void testGetCellInfoDelayTime() throws Exception {
        assertEquals(2000, LocaleTracker.getCellInfoDelayTime(0));
        assertEquals(2000, LocaleTracker.getCellInfoDelayTime(1));
        assertEquals(4000, LocaleTracker.getCellInfoDelayTime(2));
        assertEquals(8000, LocaleTracker.getCellInfoDelayTime(3));
        assertEquals(16000, LocaleTracker.getCellInfoDelayTime(4));
        assertEquals(32000, LocaleTracker.getCellInfoDelayTime(5));
        assertEquals(64000, LocaleTracker.getCellInfoDelayTime(6));
        assertEquals(128000, LocaleTracker.getCellInfoDelayTime(7));
        assertEquals(256000, LocaleTracker.getCellInfoDelayTime(8));
        assertEquals(512000, LocaleTracker.getCellInfoDelayTime(9));

        for (int i = 10; i <= 2000; i++) {
            assertEquals(600000, LocaleTracker.getCellInfoDelayTime(i));
        }
    }

    @Test
    @SmallTest
    public void updateOperatorNumeric_NoSim_shouldHandleNetworkCountryCodeUnavailable()
            throws Exception {
        mLocaleTracker.updateOperatorNumeric("");
        sendOperatorLost();
        verify(mNitzStateMachine, times(1)).handleCountryUnavailable();
    }

    @Test
    @SmallTest
    public void updateOperatorNumeric_TestNetwork_shouldHandleNetworkCountryCodeSet()
            throws Exception {
        mLocaleTracker.updateOperatorNumeric(TEST_CELL_MCC + FAKE_MNC);
        verify(mNitzStateMachine, times(1)).handleCountryDetected("");
    }
}
