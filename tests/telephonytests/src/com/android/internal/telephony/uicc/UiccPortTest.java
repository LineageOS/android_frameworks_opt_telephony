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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.Binder;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.IccLogicalChannelRequest;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UiccPortTest extends TelephonyTest {
    private static final int CHANNEL_ID = 1;

    // Mocked classes
    private UiccCard mUiccCard;
    private IccCardStatus mIccCardStatus;

    private UiccPort mUiccPort;

    private IccIoResult mIccIoResult;

    private int mPhoneId = 0;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mUiccCard = mock(UiccCard.class);
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
        mUiccPort = new UiccPort(mContext, mSimulatedCommands, mIccCardStatus,
                mPhoneId /* phoneId */, new Object(), mUiccCard);
        processAllMessages();
        logd("create UiccPort");
    }

    @After
    public void tearDown() throws Exception {
        mUiccPort = null;
        mIccIoResult = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testUiccPortdInfoCorrectness() {
        /* before update correctness test */
        assertEquals(0, mUiccPort.getNumApplications());
        assertNull(mUiccPort.getUniversalPinState());
        assertNull(mUiccPort.getOperatorBrandOverride());
        for (IccCardApplicationStatus.AppType mAppType :
                IccCardApplicationStatus.AppType.values()) {
            assertFalse(mUiccPort.isApplicationOnIcc(mAppType));
        }
        assertEquals(mPhoneId, mUiccPort.getPhoneId());
        assertEquals(TelephonyManager.DEFAULT_PORT_INDEX, mUiccPort.getPortIdx());
    }

    @Test
    @SmallTest
    public void testGetOpenLogicalChannelRecord_noChannelOpened_shouldReturnNull() {
        assertThat(mUiccPort.getOpenLogicalChannelRecord(CHANNEL_ID)).isNull();
    }

    @Test
    @SmallTest
    public void testOnLogicalChannelOpened_withChannelOpen_recordShouldMatch() {
        IccLogicalChannelRequest request = getIccLogicalChannelRequest();

        mUiccPort.onLogicalChannelOpened(request);

        UiccPort.OpenLogicalChannelRecord record = mUiccPort.getOpenLogicalChannelRecord(
                CHANNEL_ID);
        assertThat(record).isNotNull();
    }

    @Test
    @SmallTest
    public void testOnOpenLogicalChannelClosed_withChannelOpenThenClose_noRecordLeft() {
        IccLogicalChannelRequest request = getIccLogicalChannelRequest();

        mUiccPort.onLogicalChannelOpened(request);
        mUiccPort.onLogicalChannelClosed(CHANNEL_ID);

        UiccPort.OpenLogicalChannelRecord record = mUiccPort.getOpenLogicalChannelRecord(
                CHANNEL_ID);
        assertThat(record).isNull();
    }

    @Test
    @SmallTest
    public void testClientDied_withChannelOpened_shouldGetCleanup() {
        IccLogicalChannelRequest request = getIccLogicalChannelRequest();
        mUiccPort.onLogicalChannelOpened(request);

        UiccPort.OpenLogicalChannelRecord record = mUiccPort.getOpenLogicalChannelRecord(
                CHANNEL_ID);
        record.binderDied();

        record = mUiccPort.getOpenLogicalChannelRecord(CHANNEL_ID);
        assertThat(record).isNull();
        verify(mUiccProfile).iccCloseLogicalChannel(eq(CHANNEL_ID), eq(null));
    }

    private IccLogicalChannelRequest getIccLogicalChannelRequest() {
        IccLogicalChannelRequest request = new IccLogicalChannelRequest();
        request.channel = CHANNEL_ID;
        request.subId = 0;
        request.binder = new Binder();
        return request;
    }
}
