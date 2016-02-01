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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.UiccController;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class UiccControllerTest {
    private Context mContext;
    private SimulatedCommands mSimulatedCommands;
    private UiccController mUiccController;
    private static final int PHONE_COUNT = 1;
    private static int ICC_CHANGED_EVENT = 0;
    private static final String TAG = "UiccControllerTest";
    private boolean mReady;
    private Object mLock = new Object();
    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private SubscriptionController mSubscriptionController;
    @Mock
    private Handler mMockedHandler;
    @Mock
    private  IccCardStatus mIccCardStatus;

    private class UiccControllerHandlerThread extends HandlerThread {

        private UiccControllerHandlerThread(String name) {
            super(name);
        }
        @Override
        public void onLooperPrepared() {
            synchronized (mLock) {
                /* create a new UICC Controller associated with the simulated Commands */
                mUiccController = UiccController.make(mContext,
                                  new CommandsInterface[]{mSimulatedCommands});
                /* expected to get new UiccCards being created
                wait till the async result and message delay */
                TelephonyTestUtils.waitForMs(300);
                mReady = true;
                logd("create UiccCardController");
            }
        }
    }

    private void waitUntilReady() {
        while (true) {
            synchronized (mLock) {
                if (mReady) {
                    break;
                }
            }
        }
    }

    private IccCardApplicationStatus composeUiccApplicationStatus(
            IccCardApplicationStatus.AppType appType,
            IccCardApplicationStatus.AppState appState, String aid) {
        IccCardApplicationStatus mIccCardAppStatus = new IccCardApplicationStatus();
        mIccCardAppStatus.aid = aid;
        mIccCardAppStatus.app_type = appType;
        mIccCardAppStatus.app_state = appState;
        mIccCardAppStatus.pin1 = mIccCardAppStatus.pin2 =
                IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
        return mIccCardAppStatus;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new ContextFixture().getTestDouble();
        /* Radio state UNAVAIL->OFF when initialize new simulated commands*/
        mSimulatedCommands = new SimulatedCommands();

        Field field = TelephonyManager.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mTelephonyManager);
        field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSubscriptionController);
        doReturn(PHONE_COUNT).when(mTelephonyManager).getPhoneCount();
        doReturn(PHONE_COUNT).when(mTelephonyManager).getSimCount();

        /* create a new UiccCard Controller, avoid make called multiple times */
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, null);

        /* null Application associated with any FAM */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mSimulatedCommands.setIccCardStatus(mIccCardStatus);
        new UiccControllerHandlerThread(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mContext = null;
        mSimulatedCommands = null;
        mUiccController = null;
        mTelephonyManager = null;
    }

    @Test @SmallTest
    public void testSanity() {

        assertEquals(PHONE_COUNT, mUiccController.getUiccCards().length);
        assertNotNull(mUiccController.getUiccCard(0));
        assertNull(mUiccController.getIccRecords(0, UiccController.APP_FAM_3GPP));
        assertNull(mUiccController.getIccRecords(0, UiccController.APP_FAM_3GPP2));
        assertNull(mUiccController.getIccRecords(0, UiccController.APP_FAM_IMS));
        assertNull(mUiccController.getIccFileHandler(0, UiccController.APP_FAM_3GPP));
        assertNull(mUiccController.getIccFileHandler(0, UiccController.APP_FAM_3GPP2));
        assertNull(mUiccController.getIccFileHandler(0, UiccController.APP_FAM_IMS));
    }

    @Test @SmallTest
    public void testPowerOff() {
        /* Uicc Controller registered for event off to unavail */
        logd("radio power state transition from off to unavail, dispose UICC Card");
        testSanity();
        mSimulatedCommands.requestShutdown(null);
        TelephonyTestUtils.waitForMs(50);
        assertNull(mUiccController.getUiccCard(0));
        assertEquals(mSimulatedCommands.getRadioState(),
                CommandsInterface.RadioState.RADIO_UNAVAILABLE);
    }

    @Test@SmallTest
    public void testPowerOn() {
        mSimulatedCommands.setRadioPower(true, null);
        TelephonyTestUtils.waitForMs(500);
        assertNotNull(mUiccController.getUiccCard(0));
        assertEquals(mSimulatedCommands.getRadioState(), CommandsInterface.RadioState.RADIO_ON);
    }

    @Test @SmallTest
    public void testPowerOffPowerOnWithApp() {
         /* update app status and index */
        IccCardApplicationStatus cdmaApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_CSIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA0");
        IccCardApplicationStatus imsApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_ISIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA1");
        IccCardApplicationStatus umtsApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_USIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA2");
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{cdmaApp, imsApp, umtsApp};
        mIccCardStatus.mCdmaSubscriptionAppIndex = 0;
        mIccCardStatus.mImsSubscriptionAppIndex = 1;
        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = 2;

        mSimulatedCommands.setIccCardStatus(mIccCardStatus);
        logd("radio power state transition from off to unavail");
        testPowerOff();
        /* UICC controller registered for event unavailable to on */
        logd("radio power state transition from unavail to on, update IccCardStatus with app");
        testPowerOn();

        logd("validate Card status with Applications on it followed by Power on");
        assertNotNull(mUiccController.getUiccCard(0));
        assertNotNull(mUiccController.getIccRecords(0, UiccController.APP_FAM_3GPP));
        assertNotNull(mUiccController.getIccRecords(0, UiccController.APP_FAM_3GPP2));
        assertNotNull(mUiccController.getIccRecords(0, UiccController.APP_FAM_IMS));
        assertNotNull(mUiccController.getIccFileHandler(0, UiccController.APP_FAM_3GPP));
        assertNotNull(mUiccController.getIccFileHandler(0, UiccController.APP_FAM_3GPP2));
        assertNotNull(mUiccController.getIccFileHandler(0, UiccController.APP_FAM_IMS));
    }

    @Test @SmallTest
    public void testIccChangedListener() {
        mUiccController.registerForIccChanged(mMockedHandler, ICC_CHANGED_EVENT, null);
        testPowerOff();
        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long>    mCaptorLong = ArgumentCaptor.forClass(Long.class);
        verify(mMockedHandler, atLeast(1)).sendMessageDelayed(mCaptorMessage.capture(),
                mCaptorLong.capture());
        assertEquals(ICC_CHANGED_EVENT, mCaptorMessage.getValue().what);
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
