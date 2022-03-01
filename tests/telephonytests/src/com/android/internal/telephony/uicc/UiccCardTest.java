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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UiccCardTest extends TelephonyTest {

    private static final int INVALID_PORT_ID = -1;

    private UiccCard mUiccCard;

    private IccIoResult mIccIoResult;

    // Mocked classes
    private IccCardStatus mIccCardStatus;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mIccCardStatus = mock(IccCardStatus.class);
        /* initially there are no application available */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mIccCardStatus.mSlotPortMapping = new IccSlotPortMapping();
        mIccCardStatus.mSlotPortMapping.mPhysicalSlotIndex = 0;
        mIccCardStatus.mSlotPortMapping.mPortIndex = 0;
        mIccIoResult = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes("FF40"));
        mSimulatedCommands.setIccIoResultForApduLogicalChannel(mIccIoResult);
        mUiccCard = new UiccCard(mContext, mSimulatedCommands, mIccCardStatus, 0 /* phoneId */,
            new Object(), false);
        processAllMessages();
        logd("create UiccCard");
    }

    @After
    public void tearDown() throws Exception {
        mUiccCard = null;
        mIccIoResult = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testUiccCartdInfoCorrectness() {
        /* before update correctness test */
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccCard.getCardState());
        assertEquals(1, mUiccCard.getUiccPortList().length);
    }

    @Test
    @SmallTest
    public void testUpdateUiccCardState() {
        /* set card as present */
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        logd("Update UICC Card State");
        mUiccCard.update(mContext, mSimulatedCommands, mIccCardStatus, 0);
        /* try to create a new CarrierPrivilege, loading state -> loaded state */
        processAllMessages();

        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccCard.getCardState());
        // make sure duplicate UiccPort objects are not created after update API call
        assertEquals(1, mUiccCard.getUiccPortList().length);
        assertNull(mUiccCard.getUiccPort(INVALID_PORT_ID));
        assertNotNull(mUiccCard.getUiccPort(TelephonyManager.DEFAULT_PORT_INDEX));
    }
}
