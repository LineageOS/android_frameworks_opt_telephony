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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.test.filters.SmallTest;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class UiccSlotTest extends TelephonyTest {
    private UiccSlot mUiccSlot;
    private UiccSlotTestHandlerThread mTestHandlerThread;
    private Handler mTestHandler;

    @Mock
    private Handler mMockedHandler;
    @Mock
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
                            mUiccSlot.update(mSimulatedCommands, mIccCardStatus, 0 /* phoneId */);
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
        /* initially there are no application available */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;

        /* starting the Handler Thread */
        mTestHandlerThread = new UiccSlotTestHandlerThread(getClass().getSimpleName());
        mTestHandlerThread.start();

        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mTestHandlerThread.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testUpdateSlotStatus() {
        IccSlotStatus iss = new IccSlotStatus();
        iss.slotState = IccSlotStatus.SlotState.SLOTSTATE_INACTIVE;
        iss.cardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        iss.iccid = "fake-iccid";

        // initial state
        assertTrue(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_ABSENT, mUiccSlot.getCardState());
        assertNull(mUiccSlot.getIccId());

        // update slot to inactive
        mUiccSlot.update(null, iss);

        // assert on updated values
        assertFalse(mUiccSlot.isActive());
        assertNull(mUiccSlot.getUiccCard());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccSlot.getCardState());
        assertEquals(iss.iccid, mUiccSlot.getIccId());

        iss.slotState = IccSlotStatus.SlotState.SLOTSTATE_ACTIVE;

        // update slot to active
        mUiccSlot.update(mSimulatedCommands, iss);

        // assert on updated values
        assertTrue(mUiccSlot.isActive());
    }
}
