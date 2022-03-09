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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UiccSlotTest extends TelephonyTest {
    private UiccSlot mUiccSlot;
    private UiccSlotTestHandlerThread mTestHandlerThread;
    private Handler mTestHandler;

    // Mocked classes
    private IccCardStatus mIccCardStatus;

    private static final int UICCCARD_ABSENT = 1;
    private static final int UICCCARD_UPDATE_CARD_STATE_EVENT = 2;

    private class UiccSlotTestHandlerThread extends HandlerThread {
        private UiccSlotTestHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mUiccSlot = new UiccSlot(mContext, true /* isActive */);
            mTestHandler = new Handler(mTestHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case UICCCARD_UPDATE_CARD_STATE_EVENT:
                            mUiccSlot.update(mSimulatedCommands, mIccCardStatus, 0 /* phoneId */,
                                    0 /* slotIndex */);
                            setReady(true);
                            break;
                        default:
                            logd("Unknown Event " + msg.what);
                            break;
                    }
                }
            };
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mIccCardStatus = mock(IccCardStatus.class);
        mContextFixture.putBooleanResource(com.android.internal.R.bool.config_hotswapCapable, true);
        /* initially there are no application available */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mIccCardStatus.mSlotPortMapping = new IccSlotPortMapping();
        /* starting the Handler Thread */
        mTestHandlerThread = new UiccSlotTestHandlerThread(getClass().getSimpleName());
        mTestHandlerThread.start();

        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mTestHandlerThread.quit();
        mTestHandlerThread.join();
        mTestHandlerThread = null;
        mTestHandler.removeCallbacksAndMessages(null);
        mTestHandler = null;
        mUiccSlot = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testUpdateInactiveSlotStatus() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());
    }

    @Test
    @SmallTest
    public void testUpdateActiveSlotStatus() {
        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        mSimulatedCommands.setRadioPower(true, null);
        int phoneId = 0;
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = true;
        simPortInfo.mLogicalSlotIndex = phoneId;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_ABSENT;

        // update slot to inactive
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());
        verify(mSubInfoRecordUpdater).updateInternalIccState(
                IccCardConstants.INTENT_VALUE_ICC_ABSENT, null, phoneId);
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatusEuiccIsSupported() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.atr = "3F979580BFFE8210428031A073BE211797";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());

        iss.mSimPortInfos[0].mPortActive = true;

        // update slot to active
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertTrue(mUiccSlot.isEuicc());
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatusEuiccIsNotSupported() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.atr = "3F979580BFFE8110428031A073BE211797";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());

        iss.mSimPortInfos[0].mPortActive = true;

        // update slot to active
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertFalse(mUiccSlot.isEuicc());
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatusVoltageClassA() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.atr = "3b9795801F018031A073BE211500";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());

        iss.mSimPortInfos[0].mPortActive = true;

        // update slot to active
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertEquals(mUiccSlot.getMinimumVoltageClass(), UiccSlot.VOLTAGE_CLASS_A);
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatusVoltageClassANoTa() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.atr = "3b9795800F048031A073BE2115";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());

        iss.mSimPortInfos[0].mPortActive = true;

        // update slot to active
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertEquals(UiccSlot.VOLTAGE_CLASS_A, mUiccSlot.getMinimumVoltageClass());
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatusVoltageClassB() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.atr = "3b9795801F428031A073BE211500";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());

        iss.mSimPortInfos[0].mPortActive = true;

        // update slot to active
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertEquals(UiccSlot.VOLTAGE_CLASS_B, mUiccSlot.getMinimumVoltageClass());
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatusVoltageClassC() {
        IccSlotStatus iss = new IccSlotStatus();
        IccSimPortInfo simPortInfo = new IccSimPortInfo();
        simPortInfo.mPortActive = false;
        simPortInfo.mLogicalSlotIndex = 0;
        simPortInfo.mIccId = "fake-iccid";
        iss.mSimPortInfos = new IccSimPortInfo[] {simPortInfo};
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.atr = "3b9795801F048031A073BE211500";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // update slot to inactive
        mUiccSlot.update(null, iss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());

        iss.mSimPortInfos[0].mPortActive = true;

        // update slot to active
        mUiccSlot.update(new CommandsInterface[] {mSimulatedCommands}, iss, 0 /* slotIndex */);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
        assertEquals(UiccSlot.VOLTAGE_CLASS_C, mUiccSlot.getMinimumVoltageClass());
    }

    @Test
    @SmallTest
    public void testUpdateAbsentState() {
        int phoneId = 0;
        int slotIndex = 0;
        // Make sure when received CARDSTATE_ABSENT state in the first time,
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_ABSENT;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);
        verify(mSubInfoRecordUpdater).updateInternalIccState(
                IccCardConstants.INTENT_VALUE_ICC_ABSENT, null, phoneId);
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());
        assertNull(mUiccSlot.getUiccCard());
    }

    @Test
    @SmallTest
    public void testUpdateAbsentStateInactiveSlotStatus() {
        IccSlotStatus activeIss = new IccSlotStatus();
        IccSimPortInfo activePortInfo = new IccSimPortInfo();
        activePortInfo.mPortActive = true;
        activePortInfo.mLogicalSlotIndex = 0;
        activePortInfo.mIccId = "fake-iccid";
        activeIss.mSimPortInfos = new IccSimPortInfo[] {activePortInfo};
        activeIss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        IccSlotStatus inactiveIss = new IccSlotStatus();
        IccSimPortInfo inactivePortInfo = new IccSimPortInfo();
        inactivePortInfo.mPortActive = false;
        inactivePortInfo.mLogicalSlotIndex = 0;
        inactivePortInfo.mIccId = "fake-iccid";
        inactiveIss.mSimPortInfos = new IccSimPortInfo[] {inactivePortInfo};
        inactiveIss.cardState = IccCardStatus.CardState.CARDSTATE_ABSENT;

        // update slot to inactive with absent card
        mUiccSlot.update(null, activeIss, 0 /* slotIndex */);
        mUiccSlot.update(null, inactiveIss, 0 /* slotIndex */);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());

        // assert that we tried to update subscriptions
        verify(mSubInfoRecordUpdater).updateInternalIccStateForInactivePort(
                activeIss.mSimPortInfos[0].mLogicalSlotIndex, inactiveIss.mSimPortInfos[0].mIccId);
    }

    @Test
    @SmallTest
    public void testUiccSlotCreateAndDispose() {
        int phoneId = 0;
        int slotIndex = 0;
        // Simulate when SIM is added, UiccCard and UiccProfile should be created.
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mIccCardStatus.mSlotPortMapping.mPhysicalSlotIndex = slotIndex;
        mIccCardStatus.mSlotPortMapping.mPortIndex = 0;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);
        verify(mTelephonyComponentFactory).makeUiccProfile(
                anyObject(), eq(mSimulatedCommands), eq(mIccCardStatus), anyInt(), anyObject(),
                anyObject());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());
        assertNotNull(mUiccSlot.getUiccCard());

        // Simulate when SIM is removed, UiccCard and UiccProfile should be disposed and ABSENT
        // state is sent to SubscriptionInfoUpdater.
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_ABSENT;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);
        verify(mSubInfoRecordUpdater).updateInternalIccState(
                IccCardConstants.INTENT_VALUE_ICC_ABSENT, null, phoneId);
        verify(mUiccProfile).dispose();
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());
        assertNull(mUiccSlot.getUiccCard());
    }

    @Test
    @SmallTest
    public void testUiccSlotBroadcastAbsent() {
        int phoneId = 0;
        int slotIndex = 0;
        // Simulate when SIM is added, UiccCard and UiccProfile should be created.
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);
        verify(mTelephonyComponentFactory).makeUiccProfile(
                anyObject(), eq(mSimulatedCommands), eq(mIccCardStatus), anyInt(), anyObject(),
                anyObject());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());
        assertNotNull(mUiccSlot.getUiccCard());

        // radio state unavailable
        mUiccSlot.onRadioStateUnavailable(phoneId);

        // Verify that UNKNOWN state is sent to SubscriptionInfoUpdater in this case.
        verify(mSubInfoRecordUpdater).updateInternalIccState(
                IccCardConstants.INTENT_VALUE_ICC_UNKNOWN, null, phoneId);
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());
        assertNull(mUiccSlot.getUiccCard());

        // SIM removed while radio is unavailable, and then radio state on triggers update()
        mIccCardStatus.mCardState = CardState.CARDSTATE_ABSENT;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);

        // Verify that ABSENT state is sent to SubscriptionInfoUpdater in this case.
        verify(mSubInfoRecordUpdater).updateInternalIccState(
                IccCardConstants.INTENT_VALUE_ICC_ABSENT, null, phoneId);
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());
        assertNull(mUiccSlot.getUiccCard());
    }

    @Test
    public void testNotRemovable() throws InterruptedException {
        int phoneId = 0;
        int slotIndex = 0;

        // mock the resource overlay which declares the euicc slots
        mContextFixture.putIntArrayResource(com.android.internal.R.array.non_removable_euicc_slots,
                new int[]{0, 1});

        // Simulate when SIM is added, UiccCard and UiccProfile should be created.
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);

        assertFalse("EuiccCard should not be removable", mUiccSlot.isRemovable());
    }

    @Test
    public void testIsRemovable() throws InterruptedException {
        int phoneId = 0;
        int slotIndex = 0;

        // mock the resource overlay which declares the euicc slots
        mContextFixture.putIntArrayResource(com.android.internal.R.array.non_removable_euicc_slots,
                new int[]{1});

        // Simulate when SIM is added, UiccCard and UiccProfile should be created.
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mUiccSlot.update(mSimulatedCommands, mIccCardStatus, phoneId, slotIndex);

        assertTrue("EuiccCard should be removable", mUiccSlot.isRemovable());
    }

}
