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

import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UiccCardTest extends TelephonyTest {
    private UiccCard mUiccCard;

    private IccIoResult mIccIoResult;

    @Mock
    private IccCardStatus mIccCardStatus;

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
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;

        mIccIoResult = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes("FF40"));
        mSimulatedCommands.setIccIoResultForApduLogicalChannel(mIccIoResult);
        mUiccCard = new UiccCard(mContext, mSimulatedCommands, mIccCardStatus, 0 /* phoneId */,
            new Object());
        processAllMessages();
        logd("create UiccCard");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void tesUiccCartdInfoSanity() {
        /* before update sanity test */
        assertEquals(0, mUiccCard.getNumApplications());
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccCard.getCardState());
        assertNull(mUiccCard.getUniversalPinState());
        assertNull(mUiccCard.getOperatorBrandOverride());
        /* UiccProfile mock should return false */
        assertFalse(mUiccCard.areCarrierPriviligeRulesLoaded());
        for (IccCardApplicationStatus.AppType mAppType :
                IccCardApplicationStatus.AppType.values()) {
            assertFalse(mUiccCard.isApplicationOnIcc(mAppType));
        }
    }

    @Test
    @SmallTest
    public void testUpdateUiccCardState() {
        int mChannelId = 1;
        /* set card as present */
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        /* Mock open Channel ID 1 */
        mSimulatedCommands.setOpenChannelId(mChannelId);
        logd("Update UICC Card State");
        mUiccCard.update(mContext, mSimulatedCommands, mIccCardStatus);
        /* try to create a new CarrierPrivilege, loading state -> loaded state */
        processAllMessages();

        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUiccCard.getCardState());

        /* todo: This part should move to UiccProfileTest
        assertTrue(mUicccard.areCarrierPriviligeRulesLoaded());
        verify(mSimulatedCommandsVerifier, times(2)).iccOpenLogicalChannel(isA(String.class),
                anyInt(), isA(Message.class));
        verify(mSimulatedCommandsVerifier, times(2)).iccTransmitApduLogicalChannel(
                eq(mChannelId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(),
                isA(Message.class)
        );
        */
    }
}
