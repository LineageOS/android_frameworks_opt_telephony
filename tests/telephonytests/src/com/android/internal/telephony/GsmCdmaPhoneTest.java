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
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private CallManager callManager;
    @Mock
    private PhoneNotifier mNotifier;
    @Mock
    private TelephonyComponentFactory telephonyComponentFactory;
    @Mock
    CdmaSubscriptionSourceManager cdmaSSM;
    @Mock
    RegistrantList registrantList;
    @Mock
    IccPhoneBookInterfaceManager mIccPhoneBookIntManager;
    @Mock
    HashMap<Integer, ImsManager> mImsManagerInstances;

    private SimulatedCommands simulatedCommands;
    private ContextFixture contextFixture;
    private GsmCdmaPhone mPhone;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        //Use reflection to mock singleton
        Field field = CallManager.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, callManager);

        //Use reflection to mock singleton
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        //Use reflection to mock singleton
        field = CdmaSubscriptionSourceManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, cdmaSSM);

        //Use reflection to mock singleton
        field = ImsManager.class.getDeclaredField("sImsManagerInstances");
        field.setAccessible(true);
        field.set(null, mImsManagerInstances);

        field = CdmaSubscriptionSourceManager.class.getDeclaredField(
                "mCdmaSubscriptionSourceChangedRegistrants");
        field.setAccessible(true);
        field.set(cdmaSSM, registrantList);

        doReturn(mSST).when(telephonyComponentFactory).
                makeServiceStateTracker(any(GsmCdmaPhone.class), any(CommandsInterface.class));
        doReturn(mIccCardProxy).when(telephonyComponentFactory).
                makeIccCardProxy(any(Context.class), any(CommandsInterface.class), anyInt());
        doReturn(mCT).when(telephonyComponentFactory).
                makeGsmCdmaCallTracker(any(GsmCdmaPhone.class));
        doReturn(mIccPhoneBookIntManager).when(telephonyComponentFactory).
                makeIccPhoneBookInterfaceManager(any(Phone.class));
        doReturn(true).when(mImsManagerInstances).containsKey(anyInt());

        simulatedCommands = new SimulatedCommands();
        contextFixture = new ContextFixture();
        mPhone = new GsmCdmaPhone(contextFixture.getTestDouble(), simulatedCommands, mNotifier,
                true, 0, PhoneConstants.PHONE_TYPE_GSM, telephonyComponentFactory);

        doReturn(false).when(mSST).isDeviceShuttingDown();
    }

    @After
    public void tearDown() throws Exception {
        mPhone = null;
    }

    @Test @SmallTest
    public void testPhoneTypeSwitch() {
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhone.getPrecisePhoneType());
        mPhone.sendMessage(mPhone.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_IS95A}, null)));
        //wait for voice RAT to be updated
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            logd("InterruptedException while waiting for voice rat to change: " + e);
        }
        assertEquals(PhoneConstants.PHONE_TYPE_CDMA_LTE, mPhone.getPrecisePhoneType());
    }

    @Test @SmallTest
    public void testHandleActionCarrierConfigChanged() {
        // set voice radio tech in RIL to 1xRTT. ACTION_CARRIER_CONFIG_CHANGED should trigger a
        // query and change phone type
        simulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhone.getPrecisePhoneType());
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        contextFixture.sendBroadcast(intent);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            logd("InterruptedException while waiting: " + e);
        }
        assertEquals(PhoneConstants.PHONE_TYPE_CDMA_LTE, mPhone.getPrecisePhoneType());
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}