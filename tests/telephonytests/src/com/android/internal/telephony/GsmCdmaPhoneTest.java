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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.RegistrantList;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.HashMap;

public class GsmCdmaPhoneTest {
    private static final String TAG = "GsmCdmaPhoneTest";

    @Mock
    private ServiceStateTracker mSST;
    @Mock
    private GsmCdmaCallTracker mCT;
    @Mock
    private UiccController mUiccController;
    @Mock
    private IccCardProxy mIccCardProxy;
    @Mock
    private CallManager mCallManager;
    @Mock
    private PhoneNotifier mNotifier;
    @Mock
    private TelephonyComponentFactory mTelephonyComponentFactory;
    @Mock
    CdmaSubscriptionSourceManager mCdmaSSM;
    @Mock
    RegistrantList mRegistrantList;
    @Mock
    IccPhoneBookInterfaceManager mIccPhoneBookIntManager;
    @Mock
    HashMap<Integer, ImsManager> mImsManagerInstances;
    @Mock
    DcTracker mDcTracker;
    @Mock
    GsmCdmaCall mGsmCdmaCall;
    @Mock
    SubscriptionController mSubscriptionController;

    private SimulatedCommands mSimulatedCommands;
    private ContextFixture mContextFixture;
    private GsmCdmaPhone mPhone;
    private Object mLock = new Object();
    private boolean mReady;

    private class GsmCdmaPhoneTestHandler extends HandlerThread {

        private GsmCdmaPhoneTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mPhone = new GsmCdmaPhone(mContextFixture.getTestDouble(), mSimulatedCommands, mNotifier,
                    true, 0, PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory);
            synchronized (mLock) {
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

    private void switchToGsm() {
        mPhone.sendMessage(mPhone.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_GSM},
                        null)));
        //wait for voice RAT to be updated
        TelephonyTestUtils.waitForMs(50);
    }

    private void switchToCdma() {
        mPhone.sendMessage(mPhone.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_IS95A}, null)));
        //wait for voice RAT to be updated
        TelephonyTestUtils.waitForMs(50);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        //Use reflection to mock singleton
        Field field = CallManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, mCallManager);

        //Use reflection to mock singleton
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        //Use reflection to mock singleton
        field = CdmaSubscriptionSourceManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mCdmaSSM);

        //Use reflection to mock singleton
        field = ImsManager.class.getDeclaredField("sImsManagerInstances");
        field.setAccessible(true);
        field.set(null, mImsManagerInstances);

        //Use reflection to mock singleton
        field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSubscriptionController);

        field = CdmaSubscriptionSourceManager.class.getDeclaredField(
                "mCdmaSubscriptionSourceChangedRegistrants");
        field.setAccessible(true);
        field.set(mCdmaSSM, mRegistrantList);

        doReturn(mSST).when(mTelephonyComponentFactory).
                makeServiceStateTracker(any(GsmCdmaPhone.class), any(CommandsInterface.class));
        doReturn(mIccCardProxy).when(mTelephonyComponentFactory).
                makeIccCardProxy(any(Context.class), any(CommandsInterface.class), anyInt());
        doReturn(mCT).when(mTelephonyComponentFactory).
                makeGsmCdmaCallTracker(any(GsmCdmaPhone.class));
        doReturn(mIccPhoneBookIntManager).when(mTelephonyComponentFactory).
                makeIccPhoneBookInterfaceManager(any(Phone.class));
        doReturn(mDcTracker).when(mTelephonyComponentFactory).
                makeDcTracker(any(Phone.class));
        doReturn(true).when(mImsManagerInstances).containsKey(anyInt());

        mSimulatedCommands = new SimulatedCommands();
        mContextFixture = new ContextFixture();

        doReturn(false).when(mSST).isDeviceShuttingDown();

        mReady = false;
        new GsmCdmaPhoneTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mPhone = null;
    }

    @Test @SmallTest
    public void testPhoneTypeSwitch() {
        assertTrue(mPhone.isPhoneTypeGsm());
        switchToCdma();
        assertTrue(mPhone.isPhoneTypeCdmaLte());
    }

    @Test @SmallTest
    public void testHandleActionCarrierConfigChanged() {
        // set voice radio tech in RIL to 1xRTT. ACTION_CARRIER_CONFIG_CHANGED should trigger a
        // query and change phone type
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        assertTrue(mPhone.isPhoneTypeGsm());
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContextFixture.getTestDouble().sendBroadcast(intent);
        TelephonyTestUtils.waitForMs(50);
        assertTrue(mPhone.isPhoneTypeCdmaLte());
    }

    @Test @SmallTest
    public void testGetServiceState() {
        ServiceState serviceState = new ServiceState();
        mSST.mSS = serviceState;
        assertEquals(serviceState, mPhone.getServiceState());
    }

    @Test @SmallTest
    public void testGetCellLocation() {
        // GSM
        CellLocation cellLocation = new GsmCellLocation();
        doReturn(cellLocation).when(mSST).getCellLocation();
        assertEquals(cellLocation, mPhone.getCellLocation());

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
        CdmaCellLocation actualCellLocation = (CdmaCellLocation)mPhone.getCellLocation();
        assertEquals(0, actualCellLocation.getBaseStationLatitude());
        assertEquals(0, actualCellLocation.getBaseStationLongitude());

        // LOCATION_MODE_OFF
        Settings.Secure.putInt(TestApplication.getAppContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        actualCellLocation = (CdmaCellLocation)mPhone.getCellLocation();
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
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhone.getPhoneType());

        // Switch to CDMA
        switchToCdma();

        assertEquals(PhoneConstants.PHONE_TYPE_CDMA, mPhone.getPhoneType());
    }

    @Test @SmallTest
    public void testGetDataConnectionState() {
        // There are several cases possible. Testing few of them for now.
        // 1. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn != APN_TYPE_EMERGENCY
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST).getCurrentDataConnectionState();
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhone.getDataConnectionState(
                PhoneConstants.APN_TYPE_ALL));

        // 2. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY, apn
        // not enabled and not active
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhone.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN enabled, active and CONNECTED, callTracker state = idle
        doReturn(true).when(mDcTracker).isApnTypeEnabled(PhoneConstants.APN_TYPE_EMERGENCY);
        doReturn(true).when(mDcTracker).isApnTypeActive(PhoneConstants.APN_TYPE_EMERGENCY);
        doReturn(DctConstants.State.CONNECTED).when(mDcTracker).getState(
                PhoneConstants.APN_TYPE_EMERGENCY);
        mCT.mState = PhoneConstants.State.IDLE;
        assertEquals(PhoneConstants.DataState.CONNECTED, mPhone.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN enabled and CONNECTED, callTracker state != idle, !isConcurrentVoiceAndDataAllowed
        mCT.mState = PhoneConstants.State.RINGING;
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        assertEquals(PhoneConstants.DataState.SUSPENDED, mPhone.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));
    }

    @Test @SmallTest
    public void testHandleInCallMmiCommands() {
        try {
            // Switch to CDMA
            switchToCdma();

            assertFalse(mPhone.handleInCallMmiCommands("0"));

            // Switch to GSM
            switchToGsm();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            // !isInCall
            assertFalse(mPhone.handleInCallMmiCommands("0"));

            // isInCall
            doReturn(GsmCdmaCall.State.ACTIVE).when(mGsmCdmaCall).getState();
            assertTrue(mPhone.handleInCallMmiCommands("0"));

            // empty dialString
            assertFalse(mPhone.handleInCallMmiCommands(""));
            assertFalse(mPhone.handleInCallMmiCommands(null));

        } catch (Exception e) {
            fail(e.toString());
        }
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}