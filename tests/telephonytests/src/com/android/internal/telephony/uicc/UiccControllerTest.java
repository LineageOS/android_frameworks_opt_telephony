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
package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.euicc.EuiccCard;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;

public class UiccControllerTest extends TelephonyTest {
    private UiccController mUiccControllerUT;
    private UiccControllerHandlerThread mUiccControllerHandlerThread;
    private static final int PHONE_COUNT = 1;
    private static final int ICC_CHANGED_EVENT = 0;
    private static final int EVENT_GET_ICC_STATUS_DONE = 3;
    private static final int EVENT_GET_SLOT_STATUS_DONE = 4;
    @Mock
    private Handler mMockedHandler;
    @Mock
    private IccCardStatus mIccCardStatus;
    @Mock
    private UiccSlot mMockSlot;
    @Mock
    private UiccSlot mMockRemovableEuiccSlot;
    @Mock
    private UiccCard mMockCard;
    @Mock
    private EuiccCard mMockEuiccCard;

    private class UiccControllerHandlerThread extends HandlerThread {

        private UiccControllerHandlerThread(String name) {
            super(name);
        }
        @Override
        public void onLooperPrepared() {
            /* create a new UICC Controller associated with the simulated Commands */
            mUiccControllerUT = UiccController.make(mContext,
                    new CommandsInterface[]{mSimulatedCommands});
            setReady(true);
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
        super.setUp(this.getClass().getSimpleName());
        // some tests use use the shared preferences in the runner context, so reset them here
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getContext())
                .edit().clear().commit();

        doReturn(PHONE_COUNT).when(mTelephonyManager).getPhoneCount();
        doReturn(PHONE_COUNT).when(mTelephonyManager).getSimCount();
        // set number of slots to 1
        mContextFixture.putIntResource(com.android.internal.R.integer.config_num_physical_slots, 1);

        replaceInstance(UiccController.class, "mInstance", null, null);

        /* null Application associated with any FAM */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mSimulatedCommands.setIccCardStatus(mIccCardStatus);
        // for testing we pretend slotIndex is set. In reality it would be invalid on older versions
        // (before 1.2) of hal
        mIccCardStatus.physicalSlotIndex = 0;
        mUiccControllerHandlerThread = new UiccControllerHandlerThread(TAG);
        mUiccControllerHandlerThread.start();
        waitUntilReady();
        // reset sLastSlotStatus so that onGetSlotStatusDone always sees a change in the slot status
        mUiccControllerUT.sLastSlotStatus = null;
        /* expected to get new UiccCards being created
        wait till the async result and message delay */
        waitForMs(100);
    }

    @After
    public void tearDown() throws Exception {
        mUiccControllerHandlerThread.quit();
        mUiccControllerHandlerThread.join();
        super.tearDown();
    }

    /**
     * Replace num slots and euicc slots resources and reinstantiate the UiccController
     */
    private void reconfigureSlots(int numSlots, int[] nonRemovableEuiccSlots) throws Exception {
        mContextFixture.putIntResource(com.android.internal.R.integer.config_num_physical_slots,
                numSlots);
        mContextFixture.putIntArrayResource(
                com.android.internal.R.array.non_removable_euicc_slots,
                nonRemovableEuiccSlots);
        replaceInstance(UiccController.class, "mInstance", null, null);
        mUiccControllerHandlerThread.quit();
        mUiccControllerHandlerThread.join();
        mUiccControllerHandlerThread = new UiccControllerHandlerThread(TAG);
        mUiccControllerHandlerThread.start();
        waitUntilReady();
        waitForMs(100);
    }

    @Test @SmallTest
    public void testSanity() {
        // radio power is expected to be on which should trigger icc card and slot status requests
        verify(mSimulatedCommandsVerifier, times(1)).getIccCardStatus(any(Message.class));
        verify(mMockRadioConfig, times(1)).getSimSlotsStatus(any(Message.class));

        // response to getIccCardStatus should create mUiccSlots[0] and UiccCard for it, and update
        // phoneId to slotId mapping
        UiccSlot uiccSlot = mUiccControllerUT.getUiccSlot(0);
        UiccCard uiccCard = mUiccControllerUT.getUiccCardForSlot(0);
        assertNotNull(uiccSlot);
        // this assert verifies that phoneId 0 maps to slotId 0, since UiccCard object for both are
        // same
        assertEquals(uiccCard, mUiccControllerUT.getUiccCardForPhone(0));

        assertNotNull(mUiccControllerUT.getUiccCard(0));
        assertEquals(mSimRecords, mUiccControllerUT.getIccRecords(0, UiccController.APP_FAM_3GPP));
        assertEquals(mRuimRecords, mUiccControllerUT.getIccRecords(0,
                UiccController.APP_FAM_3GPP2));
        assertEquals(mIsimUiccRecords, mUiccControllerUT.getIccRecords(0,
                UiccController.APP_FAM_IMS));
        assertNull(mUiccControllerUT.getIccFileHandler(0, UiccController.APP_FAM_3GPP));
        assertNull(mUiccControllerUT.getIccFileHandler(0, UiccController.APP_FAM_3GPP2));
        assertNull(mUiccControllerUT.getIccFileHandler(0, UiccController.APP_FAM_IMS));
    }

    @Test @SmallTest
    public void testPowerOff() {
        /* Uicc Controller registered for event off to unavail */
        logd("radio power state transition from off to unavail, dispose UICC Card");
        testSanity();
        mSimulatedCommands.requestShutdown(null);
        waitForMs(50);
        assertNull(mUiccControllerUT.getUiccCard(0));
        assertEquals(TelephonyManager.RADIO_POWER_UNAVAILABLE, mSimulatedCommands.getRadioState());
    }

    @Test @SmallTest
    public void testPowerOn() {
        mSimulatedCommands.setRadioPower(true, null);
        waitForMs(500);
        assertNotNull(mUiccControllerUT.getUiccCard(0));
        assertEquals(TelephonyManager.RADIO_POWER_ON, mSimulatedCommands.getRadioState());
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
        assertNotNull(mUiccControllerUT.getUiccCard(0));
        assertNotNull(mUiccControllerUT.getIccRecords(0, UiccController.APP_FAM_3GPP));
        assertNotNull(mUiccControllerUT.getIccRecords(0, UiccController.APP_FAM_3GPP2));
        assertNotNull(mUiccControllerUT.getIccRecords(0, UiccController.APP_FAM_IMS));
        // null because getIccFileHandler() has not been mocked for mocked applications
        assertNull(mUiccControllerUT.getIccFileHandler(0, UiccController.APP_FAM_3GPP));
        assertNull(mUiccControllerUT.getIccFileHandler(0, UiccController.APP_FAM_3GPP2));
        assertNull(mUiccControllerUT.getIccFileHandler(0, UiccController.APP_FAM_IMS));
    }

    @Test @SmallTest
    public void testIccChangedListener() {
        mUiccControllerUT.registerForIccChanged(mMockedHandler, ICC_CHANGED_EVENT, null);
        testPowerOff();
        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long> mCaptorLong = ArgumentCaptor.forClass(Long.class);
        verify(mMockedHandler, atLeast(1)).sendMessageDelayed(mCaptorMessage.capture(),
                mCaptorLong.capture());
        assertEquals(ICC_CHANGED_EVENT, mCaptorMessage.getValue().what);
    }

    @Test
    public void testCardIdFromIccStatus() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(mMockCard).when(mMockSlot).getUiccCard();
        doReturn("A1B2C3D4").when(mMockCard).getIccId();
        doReturn("A1B2C3D4").when(mMockCard).getCardId();
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mMockCard).getCardState();

        // simulate card status loaded so that the UiccController sets the card ID
        IccCardStatus ics = new IccCardStatus();
        ics.setCardState(1 /* present */);
        ics.setUniversalPinState(3 /* disabled */);
        ics.atr = "abcdef0123456789abcdef";
        ics.iccid = "123451234567890";
        ics.eid = "A1B2C3D4";
        ics.physicalSlotIndex = 0;
        AsyncResult ar = new AsyncResult(null, ics, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_ICC_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the card ID was created
        assertEquals(0, mUiccControllerUT.convertToPublicCardId(ics.eid));
    }

    @Test
    public void testConvertNullCardId() {
        // trying to convert a null string should return -1
        assertEquals(TelephonyManager.UNINITIALIZED_CARD_ID,
                mUiccControllerUT.convertToPublicCardId(null));
    }

    @Test
    public void testConvertEmptyCardId() {
        // trying to convert an empty string should return -1
        assertEquals(TelephonyManager.UNINITIALIZED_CARD_ID,
                mUiccControllerUT.convertToPublicCardId(""));
    }

    @Test
    public void testCardIdFromSlotStatus() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();

        // simulate slot status loaded so that the UiccController sets the card ID
        IccSlotStatus iss = new IccSlotStatus();
        iss.setSlotState(1 /* active */);
        iss.eid = "ABADACB";
        ArrayList<IccSlotStatus> status = new ArrayList<IccSlotStatus>();
        status.add(iss);
        AsyncResult ar = new AsyncResult(null, status, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_SLOT_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the card ID was created
        assertEquals(0, mUiccControllerUT.convertToPublicCardId(iss.eid));
    }

    @Test
    public void testCardIdForDefaultEuicc() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();

        // simulate slot status loaded so that the UiccController sets the card ID
        IccSlotStatus iss = new IccSlotStatus();
        iss.setSlotState(1 /* active */);
        iss.eid = "AB123456";
        ArrayList<IccSlotStatus> status = new ArrayList<IccSlotStatus>();
        status.add(iss);
        AsyncResult ar = new AsyncResult(null, status, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_SLOT_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default cardId is the slot with the lowest slot index, even if inactive
        assertEquals(mUiccControllerUT.convertToPublicCardId(iss.eid),
                mUiccControllerUT.getCardIdForDefaultEuicc());
    }

    @Test
    public void testGetAllUiccCardInfos() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(false).when(mMockSlot).isEuicc();
        doReturn(mMockCard).when(mMockSlot).getUiccCard();
        doReturn("ASDF1234").when(mMockCard).getCardId();
        doReturn(true).when(mMockSlot).isRemovable();
        doReturn("A1B2C3D4").when(mMockCard).getCardId();
        doReturn("123451234567890").when(mMockCard).getIccId();
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mMockCard).getCardState();

        // simulate card status loaded so that the UiccController sets the card ID
        IccCardStatus ics = new IccCardStatus();
        ics.setCardState(1 /* present */);
        ics.setUniversalPinState(3 /* disabled */);
        ics.atr = "abcdef0123456789abcdef";
        ics.iccid = "123451234567890";
        ics.physicalSlotIndex = 0;
        AsyncResult ar = new AsyncResult(null, ics, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_ICC_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default cardId is the slot with the lowest slot index, even if inactive
        UiccCardInfo uiccCardInfo = new UiccCardInfo(
                false,     // isEuicc
                0,         // cardId
                null,      // eid
                ics.iccid, // iccid
                0,         // slotIndex
                true);     // isRemovable
        assertEquals(uiccCardInfo, mUiccControllerUT.getAllUiccCardInfos().get(0));
    }

    @Test
    public void testIccidWithTrailingF() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(false).when(mMockSlot).isEuicc();
        doReturn(mMockCard).when(mMockSlot).getUiccCard();
        doReturn("ASDF1234").when(mMockCard).getCardId();
        doReturn(true).when(mMockSlot).isRemovable();
        doReturn("A1B2C3D4").when(mMockCard).getCardId();
        doReturn("123451234567890F").when(mMockCard).getIccId();
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mMockCard).getCardState();

        // simulate card status loaded so that the UiccController sets the card ID
        IccCardStatus ics = new IccCardStatus();
        ics.setCardState(1 /* present */);
        ics.setUniversalPinState(3 /* disabled */);
        ics.atr = "abcdef0123456789abcdef";
        ics.iccid = "123451234567890F";
        ics.physicalSlotIndex = 0;
        AsyncResult ar = new AsyncResult(null, ics, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_ICC_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default cardId is the slot with the lowest slot index, even if inactive
        UiccCardInfo uiccCardInfo = new UiccCardInfo(
                false,     // isEuicc
                0,         // cardId
                null,      // eid
                IccUtils.stripTrailingFs(ics.iccid), //iccid without ending F
                0,         // slotIndex
                true);     // isRemovable
        assertEquals(uiccCardInfo, mUiccControllerUT.getAllUiccCardInfos().get(0));
    }

    @Test
    public void testGetAllUiccCardInfosNullCard() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();
        doReturn(null).when(mMockSlot).getUiccCard();
        doReturn("123451234567890").when(mMockSlot).getIccId();
        doReturn(false).when(mMockSlot).isRemovable();

        // simulate card status loaded so that the UiccController sets the card ID
        IccCardStatus ics = new IccCardStatus();
        ics.setCardState(1 /* present */);
        ics.setUniversalPinState(3 /* disabled */);
        ics.atr = "abcdef0123456789abcdef";
        ics.iccid = "123451234567890";
        ics.eid = "A1B2C3D4";
        AsyncResult ar = new AsyncResult(null, ics, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_ICC_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the getAllUiccCardInfos uses info from the UiccSlot when the card is null
        UiccCardInfo uiccCardInfo = new UiccCardInfo(
                true,                                   // isEuicc
                TelephonyManager.UNINITIALIZED_CARD_ID, // cardId
                null,                                   // eid
                ics.iccid,                              // iccid
                0,                                      // slotIndex
                false);                                 // isRemovable
        assertEquals(uiccCardInfo, mUiccControllerUT.getAllUiccCardInfos().get(0));
    }

    @Test
    public void testEidNotSupported() {
        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();
        doReturn(mMockEuiccCard).when(mMockSlot).getUiccCard();
        doReturn(null).when(mMockEuiccCard).getEid();

        // simulate card status loaded so that the UiccController sets the card ID
        IccCardStatus ics = new IccCardStatus();
        ics.setCardState(1 /* present */);
        ics.setUniversalPinState(3 /* disabled */);
        ics.atr = "abcdef0123456789abcdef";
        ics.iccid = "123451234567890";
        // make it seem like EID is not supported by setting physical slot = -1 like on HAL < 1.2
        ics.physicalSlotIndex = UiccController.INVALID_SLOT_ID;
        AsyncResult ar = new AsyncResult(null, ics, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_ICC_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default eUICC card Id is UNSUPPORTED_CARD_ID
        assertEquals(TelephonyManager.UNSUPPORTED_CARD_ID,
                mUiccControllerUT.getCardIdForDefaultEuicc());
    }

    /**
     * The default eUICC should not be the removable slot if there is a built-in eUICC.
     */
    @Test
    public void testDefaultEuiccIsNotRemovable() {
        try {
            reconfigureSlots(2, new int[]{ 1 } /* non-removable slot */);
        } catch (Exception e) {
            fail("Unable to reconfigure slots.");
        }

        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots so that [0] is a removable eUICC and [1] is built-in
        mUiccControllerUT.mUiccSlots[0] = mMockRemovableEuiccSlot;
        doReturn(true).when(mMockRemovableEuiccSlot).isEuicc();
        doReturn(true).when(mMockRemovableEuiccSlot).isRemovable();
        mUiccControllerUT.mUiccSlots[1] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();
        doReturn(false).when(mMockSlot).isRemovable();

        // simulate slot status loaded so that the UiccController sets the card ID
        IccSlotStatus iss1 = new IccSlotStatus();
        iss1.setSlotState(1 /* active */);
        iss1.eid = "AB123456";
        IccSlotStatus iss2 = new IccSlotStatus();
        iss2.setSlotState(1 /* active */);
        iss2.eid = "ZYW13094";
        ArrayList<IccSlotStatus> status = new ArrayList<IccSlotStatus>();
        status.add(iss1);
        status.add(iss2);
        AsyncResult ar = new AsyncResult(null, status, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_SLOT_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default eUICC is the non-removable eUICC
        assertEquals(mUiccControllerUT.convertToPublicCardId(iss2.eid),
                mUiccControllerUT.getCardIdForDefaultEuicc());
        assertTrue(mUiccControllerUT.convertToPublicCardId(iss2.eid) >= 0);
    }

    /**
     * The default eUICC should not be the removable slot if there is a built-in eUICC. This should
     * not depend on the order of the slots.
     */
    @Test
    public void testDefaultEuiccIsNotRemovable_swapSlotOrder() {
        try {
            reconfigureSlots(2, new int[]{ 0 } /* non-removable slot */);
        } catch (Exception e) {
            fail("Unable to reconfigure slots.");
        }

        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots so that [0] is a built-in eUICC and [1] is removable
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();
        doReturn(false).when(mMockSlot).isRemovable();
        mUiccControllerUT.mUiccSlots[1] = mMockRemovableEuiccSlot;
        doReturn(true).when(mMockRemovableEuiccSlot).isEuicc();
        doReturn(true).when(mMockRemovableEuiccSlot).isRemovable();

        // simulate slot status loaded so that the UiccController sets the card ID
        IccSlotStatus iss1 = new IccSlotStatus();
        iss1.setSlotState(1 /* active */);
        iss1.eid = "AB123456";
        IccSlotStatus iss2 = new IccSlotStatus();
        iss2.setSlotState(1 /* active */);
        iss2.eid = "ZYW13094";
        ArrayList<IccSlotStatus> status = new ArrayList<IccSlotStatus>();
        status.add(iss1);
        status.add(iss2);
        AsyncResult ar = new AsyncResult(null, status, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_SLOT_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default eUICC is the non-removable eUICC
        assertEquals(mUiccControllerUT.convertToPublicCardId(iss1.eid),
                mUiccControllerUT.getCardIdForDefaultEuicc());
        assertTrue(mUiccControllerUT.convertToPublicCardId(iss1.eid) >= 0);
    }

    /**
     * The default eUICC should not be the removable slot if there is a built-in eUICC, even if that
     * eUICC is inactive.
     * When there is a built-in eUICC which is inactive, we set the mDefaultEuiccCardId to
     * TEMPORARILY_UNSUPPORTED_CARD_ID.
     */
    @Test
    public void testDefaultEuiccIsNotRemovable_EuiccIsInactive() {
        try {
            reconfigureSlots(2, new int[]{ 1 } /* non-removable slot */);
        } catch (Exception e) {
            fail();
        }

        // Give UiccController a real context so it can use shared preferences
        mUiccControllerUT.mContext = InstrumentationRegistry.getContext();

        // Mock out UiccSlots. Slot 0 is inactive here.
        mUiccControllerUT.mUiccSlots[0] = mMockSlot;
        doReturn(true).when(mMockSlot).isEuicc();
        doReturn(false).when(mMockSlot).isRemovable();
        mUiccControllerUT.mUiccSlots[1] = mMockRemovableEuiccSlot;
        doReturn(true).when(mMockSlot).isEuicc();
        doReturn(true).when(mMockSlot).isRemovable();

        // simulate slot status loaded so that the UiccController sets the card ID
        IccSlotStatus iss1 = new IccSlotStatus();
        iss1.setSlotState(0 /* active */);
        iss1.eid = "AB123456";
        IccSlotStatus iss2 = new IccSlotStatus();
        iss2.setSlotState(1 /* active */);
        iss2.eid = "ZYW13094";
        ArrayList<IccSlotStatus> status = new ArrayList<IccSlotStatus>();
        status.add(iss1);
        status.add(iss2);
        AsyncResult ar = new AsyncResult(null, status, null);
        Message msg = Message.obtain(mUiccControllerUT, EVENT_GET_SLOT_STATUS_DONE, ar);
        mUiccControllerUT.handleMessage(msg);

        // assert that the default eUICC cardId is TEMPORARILY_UNSUPPORTED_CARD_ID
        assertEquals(TelephonyManager.UNSUPPORTED_CARD_ID,
                mUiccControllerUT.getCardIdForDefaultEuicc());
    }
}
