/*
 * Copyright 2017 The Android Open Source Project
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
package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.cat.CatService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class UiccProfileTest extends TelephonyTest {
    private UiccProfile mUiccProfile;

    public UiccProfileTest() {
        super();
    }

    private IccIoResult mIccIoResult;

    private UiccProfileHandlerThread mTestHandlerThread;
    private Handler mHandler;
    private static final int UICCPROFILE_UPDATE_PROFILE_EVENT = 1;
    private static final int UICCPROFILE_UPDATE_APPLICATION_EVENT = 2;
    private static final int UICCPROFILE_CARRIER_PRIVILEDGE_LOADED_EVENT = 3;

    @Mock
    private CatService mCAT;
    @Mock
    private IccCardStatus mIccCardStatus;
    @Mock
    private Handler mMockedHandler;
    @Mock
    private UiccCard mUiccCard;

    private class UiccProfileHandlerThread extends HandlerThread {

        private UiccProfileHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mUiccProfile = new UiccProfile(mContextFixture.getTestDouble(),
                                           mSimulatedCommands, mIccCardStatus, 0 /* phoneId */,
                                           mUiccCard);
            /* create a custom handler for the Handler Thread */
            mHandler = new Handler(mTestHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case UICCPROFILE_UPDATE_PROFILE_EVENT:
                            /* Upon handling this event, new CarrierPrivilegeRule
                            will be created with the looper of HandlerThread */
                            logd("Update UICC Profile");
                            mUiccProfile.update(mContextFixture.getTestDouble(),
                                    mSimulatedCommands, mIccCardStatus);
                            setReady(true);
                            break;
                        case UICCPROFILE_UPDATE_APPLICATION_EVENT:
                            logd("Update UICC Profile Applications");
                            mUiccProfile.update(mContextFixture.getTestDouble(),
                                    mSimulatedCommands, mIccCardStatus);
                            setReady(true);
                            break;
                        default:
                            logd("Unknown Event " + msg.what);
                    }
                }
            };
            /* wait for the carrier privilege rules to be loaded */
            waitForMs(50);
            setReady(true);
            logd("Create UiccProfile");
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
        super.setUp(getClass().getSimpleName());
        /* initially there are no application available */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mIccIoResult = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes("FF40"));
        mSimulatedCommands.setIccIoResultForApduLogicalChannel(mIccIoResult);
        /* starting the Handler Thread */
        mTestHandlerThread = new UiccProfileHandlerThread(TAG);
        mTestHandlerThread.start();

        waitUntilReady();

        /* wait for the carrier privilege rules to be loaded */
        waitForMs(50);

        replaceInstance(UiccProfile.class, "mCatService", mUiccProfile, mCAT);
    }

    @After
    public void tearDown() throws Exception {
        mTestHandlerThread.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void tesUiccProfileInfoSanity() {
        assertEquals(0, mUiccProfile.getNumApplications());
        assertNull(mUiccProfile.getUniversalPinState());
        assertNull(mUiccProfile.getOperatorBrandOverride());
        for (IccCardApplicationStatus.AppType mAppType :
                IccCardApplicationStatus.AppType.values()) {
            assertFalse(mUiccProfile.isApplicationOnIcc(mAppType));
        }
    }

    @Test
    @SmallTest
    public void testUpdateUiccProfileApplication() {
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
        Message mProfileUpdate = mHandler.obtainMessage(UICCPROFILE_UPDATE_APPLICATION_EVENT);
        setReady(false);
        mProfileUpdate.sendToTarget();

        waitUntilReady();

        /* wait for the carrier privilege rules to be loaded */
        waitForMs(50);

        assertEquals(3, mUiccProfile.getNumApplications());
        assertTrue(mUiccProfile.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_CSIM));
        assertTrue(mUiccProfile.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_ISIM));
        assertTrue(mUiccProfile.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_USIM));
    }

    @Test
    @SmallTest
    public void testUpdateUiccProfile() {
        int mChannelId = 1;
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mSimulatedCommands.setOpenChannelId(mChannelId);
        Message mCardUpdate = mHandler.obtainMessage(UICCPROFILE_UPDATE_PROFILE_EVENT);
        setReady(false);
        mCardUpdate.sendToTarget();
        /* try to create a new CarrierPrivilege, loading state -> loaded state */
        /* wait till the async result and message delay */
        waitUntilReady();
        /* wait for the carrier privilege rules to be loaded */
        waitForMs(50);

        assertTrue(mUiccProfile.areCarrierPriviligeRulesLoaded());
        verify(mSimulatedCommandsVerifier, times(2)).iccOpenLogicalChannel(isA(String.class),
                anyInt(), isA(Message.class));
        verify(mSimulatedCommandsVerifier, times(2)).iccTransmitApduLogicalChannel(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(),
                isA(Message.class)
        );
    }

    @Test
    @SmallTest
    public void testUpdateUiccProfilePinState() {
        mIccCardStatus.mUniversalPinState = IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
        mUiccProfile.update(mContextFixture.getTestDouble(), mSimulatedCommands, mIccCardStatus);
        assertEquals(IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED,
                mUiccProfile.getUniversalPinState());
    }

    @Test
    @SmallTest
    public void testCarrierPriviledgeLoadedListener() {
        mUiccProfile.registerForCarrierPrivilegeRulesLoaded(mMockedHandler,
                UICCPROFILE_CARRIER_PRIVILEDGE_LOADED_EVENT, null);
        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long> mCaptorLong = ArgumentCaptor.forClass(Long.class);
        testUpdateUiccProfile();
        verify(mMockedHandler, atLeast(1)).sendMessageDelayed(mCaptorMessage.capture(),
                mCaptorLong.capture());
        assertEquals(UICCPROFILE_CARRIER_PRIVILEDGE_LOADED_EVENT, mCaptorMessage.getValue().what);
    }
}
