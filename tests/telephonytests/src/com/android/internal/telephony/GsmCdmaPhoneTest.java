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

import android.content.Intent;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.test.suitebuilder.annotation.SmallTest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GsmCdmaPhoneTest extends TelephonyTest {
    //mPhoneUnderTest
    private GsmCdmaPhone mPhoneUT;

    private class GsmCdmaPhoneTestHandler extends HandlerThread {

        private GsmCdmaPhoneTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mPhoneUT = new GsmCdmaPhone(mContextFixture.getTestDouble(), mSimulatedCommands,
                    mNotifier, true, 0, PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory);
            setReady(true);
        }
    }

    private void switchToGsm() {
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_GSM}, null)));
        //wait for voice RAT to be updated
        TelephonyTestUtils.waitForMs(50);
    }

    private void switchToCdma() {
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_IS95A}, null)));
        //wait for voice RAT to be updated
        TelephonyTestUtils.waitForMs(50);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("GsmCdmaPhoneTest");

        doReturn(false).when(mSST).isDeviceShuttingDown();

        new GsmCdmaPhoneTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mPhoneUT = null;
    }

    @Test @SmallTest
    public void testPhoneTypeSwitch() {
        assertTrue(mPhoneUT.isPhoneTypeGsm());
        switchToCdma();
        assertTrue(mPhoneUT.isPhoneTypeCdmaLte());
    }

    @Test @SmallTest
    public void testHandleActionCarrierConfigChanged() {
        // set voice radio tech in RIL to 1xRTT. ACTION_CARRIER_CONFIG_CHANGED should trigger a
        // query and change phone type
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        assertTrue(mPhoneUT.isPhoneTypeGsm());
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContextFixture.getTestDouble().sendBroadcast(intent);
        TelephonyTestUtils.waitForMs(50);
        assertTrue(mPhoneUT.isPhoneTypeCdmaLte());
    }

    @Test @SmallTest
    public void testGetServiceState() {
        ServiceState serviceState = new ServiceState();
        mSST.mSS = serviceState;
        assertEquals(serviceState, mPhoneUT.getServiceState());
    }

    @Test @SmallTest
    public void testGetCellLocation() {
        // GSM
        CellLocation cellLocation = new GsmCellLocation();
        doReturn(cellLocation).when(mSST).getCellLocation();
        assertEquals(cellLocation, mPhoneUT.getCellLocation());

        // Switch to CDMA
        switchToCdma();

        CdmaCellLocation cdmaCellLocation = new CdmaCellLocation();
        cdmaCellLocation.setCellLocationData(0, 0, 0, 0, 0);
        mSST.mCellLoc = cdmaCellLocation;

        int origValue = Settings.Secure.getInt(TestApplication.getAppContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);

        // LOCATION_MODE_ON
        Settings.Secure.putInt(TestApplication.getAppContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
        CdmaCellLocation actualCellLocation = (CdmaCellLocation) mPhoneUT.getCellLocation();
        assertEquals(0, actualCellLocation.getBaseStationLatitude());
        assertEquals(0, actualCellLocation.getBaseStationLongitude());

        // LOCATION_MODE_OFF
        Settings.Secure.putInt(TestApplication.getAppContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        actualCellLocation = (CdmaCellLocation) mPhoneUT.getCellLocation();
        assertEquals(CdmaCellLocation.INVALID_LAT_LONG,
                actualCellLocation.getBaseStationLatitude());
        assertEquals(CdmaCellLocation.INVALID_LAT_LONG,
                actualCellLocation.getBaseStationLongitude());

        // reset to origValue
        Settings.Secure.putInt(TestApplication.getAppContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, origValue);
    }

    @Test @SmallTest
    public void testGetPhoneType() {
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());

        // Switch to CDMA
        switchToCdma();

        assertEquals(PhoneConstants.PHONE_TYPE_CDMA, mPhoneUT.getPhoneType());
    }

    @Test @SmallTest
    public void testGetDataConnectionState() {
        // There are several cases possible. Testing few of them for now.
        // 1. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn != APN_TYPE_EMERGENCY
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST).getCurrentDataConnectionState();
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_ALL));

        // 2. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY, apn
        // not enabled and not active
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN enabled, active and CONNECTED, callTracker state = idle
        doReturn(true).when(mDcTracker).isApnTypeEnabled(PhoneConstants.APN_TYPE_EMERGENCY);
        doReturn(true).when(mDcTracker).isApnTypeActive(PhoneConstants.APN_TYPE_EMERGENCY);
        doReturn(DctConstants.State.CONNECTED).when(mDcTracker).getState(
                PhoneConstants.APN_TYPE_EMERGENCY);
        mCT.mState = PhoneConstants.State.IDLE;
        assertEquals(PhoneConstants.DataState.CONNECTED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN enabled and CONNECTED, callTracker state != idle, !isConcurrentVoiceAndDataAllowed
        mCT.mState = PhoneConstants.State.RINGING;
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        assertEquals(PhoneConstants.DataState.SUSPENDED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));
    }

    @Test @SmallTest
    public void testHandleInCallMmiCommands() {
        try {
            // Switch to CDMA
            switchToCdma();

            assertFalse(mPhoneUT.handleInCallMmiCommands("0"));

            // Switch to GSM
            switchToGsm();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            // !isInCall
            assertFalse(mPhoneUT.handleInCallMmiCommands("0"));

            // isInCall
            doReturn(GsmCdmaCall.State.ACTIVE).when(mGsmCdmaCall).getState();
            assertTrue(mPhoneUT.handleInCallMmiCommands("0"));

            // empty dialString
            assertFalse(mPhoneUT.handleInCallMmiCommands(""));
            assertFalse(mPhoneUT.handleInCallMmiCommands(null));

        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testDial() {
        try {
            mSST.mSS = mServiceState;
            doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            Connection connection = mPhoneUT.dial("1234567890", 0);
            verify(mCT).dial("1234567890", null, null);
        } catch(CallStateException e) {
            fail();
        }
    }

    @Test @SmallTest
    public void testHandlePinMmi() {
        assertFalse(mPhoneUT.handlePinMmi("1234567890"));
    }

    @Test @SmallTest
    public void testSendBurstDtmf() {
        //Should do nothing for GSM
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(anyString(), anyInt(), anyInt(),
                any(Message.class));

        switchToCdma();
        //invalid character
        mPhoneUT.sendBurstDtmf("12345a67890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(anyString(), anyInt(), anyInt(),
                any(Message.class));

        //state IDLE
        mCT.mState = PhoneConstants.State.IDLE;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(anyString(), anyInt(), anyInt(),
                any(Message.class));

        //state RINGING
        mCT.mState = PhoneConstants.State.RINGING;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(anyString(), anyInt(), anyInt(),
                any(Message.class));

        mCT.mState = PhoneConstants.State.OFFHOOK;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier).sendBurstDtmf("1234567890", 0, 0, null);
    }
}