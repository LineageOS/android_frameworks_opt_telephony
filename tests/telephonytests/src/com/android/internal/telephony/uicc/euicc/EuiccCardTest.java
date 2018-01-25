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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.service.euicc.EuiccProfileInfo;
import android.util.ExceptionUtils;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.euicc.apdu.LogicalChannelMocker;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EuiccCardTest extends TelephonyTest {
    private static final long WAIT_TIMEOUT_MLLIS = 5000;

    private static class ResultCaptor<T> extends AsyncResultCallback<T> {
        public T result;
        public Throwable exception;

        private CountDownLatch mLatch;

        private ResultCaptor() {
            mLatch = new CountDownLatch(1);
        }

        public void await() {
            try {
                mLatch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                fail("Execution is interrupted: " + e);
            }
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

    private class UiccCardHandlerThread extends HandlerThread {
        private UiccCardHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mEuiccCard = new EuiccCard(mContextFixture.getTestDouble(), mMockCi, mMockIccCardStatus,
                    0 /* phoneId */);
            mHandler = new Handler(mTestHandlerThread.getLooper());
            setReady(true);
        }
    }

    @Mock
    private CommandsInterface mMockCi;
    @Mock
    private IccCardStatus mMockIccCardStatus;

    private UiccCardHandlerThread mTestHandlerThread;
    private Handler mHandler;

    private EuiccCard mEuiccCard;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mMockIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mMockIccCardStatus.mCdmaSubscriptionAppIndex =
                mMockIccCardStatus.mImsSubscriptionAppIndex =
                        mMockIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mMockIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;

        mTestHandlerThread = new UiccCardHandlerThread(getClass().getSimpleName());
        mTestHandlerThread.start();

        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mTestHandlerThread.quit();
        super.tearDown();
    }

    private void assertUnexpectedException(Throwable e) {
        if (e != null) {
            fail("Unexpected exception: " + ExceptionUtils.getCompleteMessage(e) + "\n-----\n"
                    + Log.getStackTraceString(e.getCause()) + "-----");
        }
    }

    @Test
    public void testGetAllProfiles() {
        int channel = mockLogicalChannelResponses(
                "BF2D14A012E3105A0A896700000000004523019F7001019000");

        ResultCaptor<EuiccProfileInfo[]> resultCaptor = new ResultCaptor<>();
        mEuiccCard.getAllProfiles(resultCaptor, mHandler);
        resultCaptor.await();

        assertUnexpectedException(resultCaptor.exception);
        EuiccProfileInfo[] profiles = resultCaptor.result;
        assertEquals(1, profiles.length);
        assertEquals("98760000000000543210", profiles[0].getIccid());
        assertEquals(EuiccProfileInfo.PROFILE_STATE_ENABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
    }

    @Test
    public void testFSuffix() {
        // iccID is 987600000000005432FF.
        int channel = mockLogicalChannelResponses(
                "BF2D14A012E3105A0A896700000000004523FF9F7001019000");

        ResultCaptor<EuiccProfileInfo[]> resultCaptor = new ResultCaptor<>();
        mEuiccCard.getAllProfiles(resultCaptor, mHandler);
        resultCaptor.await();

        assertUnexpectedException(resultCaptor.exception);
        EuiccProfileInfo[] profiles = resultCaptor.result;
        assertEquals(1, profiles.length);
        assertEquals("987600000000005432", profiles[0].getIccid());
        assertEquals(EuiccProfileInfo.PROFILE_STATE_ENABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
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
