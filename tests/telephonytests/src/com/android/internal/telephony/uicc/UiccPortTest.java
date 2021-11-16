/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.telephony.TelephonyManager;
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
public class UiccPortTest extends TelephonyTest {
    @Mock
    private UiccCard mUiccCard;
    private UiccPort mUiccPort;

    private IccIoResult mIccIoResult;

    @Mock
    private IccCardStatus mIccCardStatus;
    private int mPhoneId = 0;

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
        mUiccPort = new UiccPort(mContext, mSimulatedCommands, mIccCardStatus,
                mPhoneId /* phoneId */, new Object(), mUiccCard);
        processAllMessages();
        logd("create UiccPort");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testUiccPortdInfoCorrectness() {
        /* before update correctness test */
        assertEquals(0, mUiccPort.getNumApplications());
        assertNull(mUiccPort.getUniversalPinState());
        assertNull(mUiccPort.getOperatorBrandOverride());
        /* UiccProfile mock should return false */
        assertFalse(mUiccPort.areCarrierPrivilegeRulesLoaded());
        for (IccCardApplicationStatus.AppType mAppType :
                IccCardApplicationStatus.AppType.values()) {
            assertFalse(mUiccPort.isApplicationOnIcc(mAppType));
        }
        assertEquals(mPhoneId, mUiccPort.getPhoneId());
        assertEquals(TelephonyManager.DEFAULT_PORT_INDEX, mUiccPort.getPortIdx());
    }
}
