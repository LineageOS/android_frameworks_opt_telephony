/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.telephony.uicc.euicc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccSlotPortMapping;
import com.android.internal.telephony.uicc.euicc.apdu.LogicalChannelMocker;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class EuiccCardTest extends TelephonyTest {

    private static class ResultCaptor<T> extends AsyncResultCallback<T> {
        public T result;
        public Throwable exception;

        private CountDownLatch mLatch;

        private ResultCaptor() {
            mLatch = new CountDownLatch(1);
        }

        @Override
        public void onResult(T r) {
            result = r;
            mLatch.countDown();
        }

        @Override
        public void onException(Throwable e) {
            exception = e;
            mLatch.countDown();
        }
    }

    // Mocked classes
    private CommandsInterface mMockCi;
    private IccCardStatus mMockIccCardStatus;

    private Handler mHandler;

    private EuiccCard mEuiccCard;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockCi = mock(CommandsInterface.class);
        mMockIccCardStatus = mock(IccCardStatus.class);

        mMockIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mMockIccCardStatus.mCdmaSubscriptionAppIndex =
                mMockIccCardStatus.mImsSubscriptionAppIndex =
                        mMockIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mMockIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        mMockIccCardStatus.mSlotPortMapping = new IccSlotPortMapping();

        mEuiccCard =
            new EuiccCard(mContext, mMockCi, mMockIccCardStatus,
                0 /* phoneId */, new Object(), false) {

                @Override
                protected void loadEidAndNotifyRegistrants() {}
            };
        mHandler = new Handler(Looper.myLooper());
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        mEuiccCard = null;
        super.tearDown();
    }

    @Test
    public void testEuiccCardInfoCorrectness() {
        /* before update correctness test */
        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mEuiccCard.getCardState());
        assertEquals(1, mEuiccCard.getUiccPortList().length);
        // TODO once MEP HAL changes are done, modify port index in cardStatus
        mEuiccCard.update(mContext, mSimulatedCommands, mMockIccCardStatus, 1);
        processAllMessages();
        /*After updating EuiccCard with port index and phoneId */
        // TODO once the port index is change,update the expected values
        assertEquals(1, mEuiccCard.getUiccPortList().length);
        assertEquals(0, mEuiccCard.getUiccPortList()[0].getPhoneId());
        // TODO uncomment below assertion check along with above MEP HAL changes.
        //assertNotEquals(mEuiccCard.getUiccPortList()[0].getPortId(),
        // mEuiccCard.getUiccPortList()[1].getPortId());
    }
    @Test
    public void testPassEidInConstructor() {
        mMockIccCardStatus.eid = "1A2B3C4D";
        mEuiccCard = new EuiccCard(mContextFixture.getTestDouble(), mMockCi,
                mMockIccCardStatus, 0 /* phoneId */, new Object(), false);

        final int eventEidReady = 0;
        Handler handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == eventEidReady) {
                    assertEquals("1A2B3C4D", mEuiccCard.getEid());
                }
            }
        };
        // This will instantly return, since EID is already set
        mEuiccCard.registerForEidReady(handler, eventEidReady, null /* obj */);
        processAllMessages();
    }

    @Test
    public void testLoadEidAndNotifyRegistrants() {
        int channel = mockLogicalChannelResponses("BF3E065A041A2B3C4D9000");
        mHandler.post(() -> {
            mEuiccCard = new EuiccCard(mContextFixture.getTestDouble(), mMockCi,
                    mMockIccCardStatus, 0 /* phoneId */, new Object(), false);
        });
        processAllMessages();

        final int eventEidReady = 0;
        Handler handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == eventEidReady) {
                    assertEquals("1A2B3C4D", mEuiccCard.getEid());
                }
            }
        };

        mEuiccCard.registerForEidReady(handler, eventEidReady, null /* obj */);
        processAllMessages();
        verifyStoreData(channel, "BF3E035C015A");
    }

    @Test
    public void testGetEid() {
        int channel = mockLogicalChannelResponses("BF3E065A041A2B3C4D9000");

        ResultCaptor<String> resultCaptor = new ResultCaptor<>();
        ((EuiccPort) mEuiccCard.getUiccPort(0)).getEid(resultCaptor, mHandler);
        processAllMessages();

        assertEquals("1A2B3C4D", resultCaptor.result);
        verifyStoreData(channel, "BF3E035C015A");
    }

    private void verifyStoreData(int channel, String command) {
        verify(mMockCi, times(1))
                .iccTransmitApduLogicalChannel(eq(channel), eq(0x80 | channel), eq(0xE2), eq(0x91),
                        eq(0), eq(command.length() / 2), eq(command), any());
    }

    private int mockLogicalChannelResponses(Object... responses) {
        int channel = LogicalChannelMocker.mockOpenLogicalChannelResponse(mMockCi,
                "E00582030200009000");
        LogicalChannelMocker.mockSendToLogicalChannel(mMockCi, channel, responses);
        LogicalChannelMocker.mockCloseLogicalChannel(mMockCi, channel);
        return channel;
    }
}