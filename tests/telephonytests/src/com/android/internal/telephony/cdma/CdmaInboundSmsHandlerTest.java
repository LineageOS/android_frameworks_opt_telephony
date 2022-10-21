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

package com.android.internal.telephony.cdma;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncResult;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Telephony;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.MediumTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.FakeSmsContentProvider;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;
import com.android.internal.util.HexDump;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CdmaInboundSmsHandlerTest extends TelephonyTest {
    // Mocked classes
    private SmsStorageMonitor mSmsStorageMonitor;
    private android.telephony.SmsMessage mSmsMessage;
    private SmsMessage mCdmaSmsMessage;

    private CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private SmsEnvelope mSmsEnvelope = new SmsEnvelope();
    private FakeSmsContentProvider mContentProvider;
    private InboundSmsTracker mInboundSmsTracker;
    private final byte[] mSmsPdu = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private final int mSubId0 = 0;

    private IState getCurrentState() {
        try {
            Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
            method.setAccessible(true);
            return (IState) method.invoke(mCdmaInboundSmsHandler);
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSmsStorageMonitor = mock(SmsStorageMonitor.class);
        mSmsMessage = mock(android.telephony.SmsMessage.class);
        mCdmaSmsMessage = mock(SmsMessage.class);

        Field field = SmsMessage.class.getDeclaredField("mEnvelope");
        field.setAccessible(true);
        field.set(mCdmaSmsMessage, mSmsEnvelope);

        UserManager userManager = (UserManager) mContextFixture.getTestDouble().
                getSystemService(Context.USER_SERVICE);
        doReturn(true).when(userManager).isUserUnlocked();

        try {
            doReturn(new int[]{UserHandle.USER_SYSTEM}).when(mIActivityManager).getRunningUserIds();
        } catch (RemoteException re) {
            fail("Unexpected RemoteException: " + re.getStackTrace());
        }

        mSmsMessage.mWrappedSmsMessage = mCdmaSmsMessage;
        doReturn(mSmsPdu).when(mCdmaSmsMessage).getPdu();

        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();

        mInboundSmsTracker = new InboundSmsTracker(
                mContext,
                mSmsPdu, /* pdu */
                System.currentTimeMillis(), /* timestamp */
                -1, /* destPort */
                true, /* is3gpp2 */
                false, /* is3gpp2WapPdu */
                "1234567890", /* address */
                "1234567890", /* displayAddress */
                "This is the message body of a single-part message" /* messageBody */,
                false, /* isClass0 */
                mSubId0,
                InboundSmsHandler.SOURCE_NOT_INJECTED);

        doReturn(mInboundSmsTracker).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(Context.class), nullable(byte[].class), anyLong(),
                anyInt(), anyBoolean(),
                anyBoolean(), nullable(String.class), nullable(String.class),
                nullable(String.class), anyBoolean(), anyInt(), anyInt());
        doReturn(mInboundSmsTracker).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(Context.class), nullable(byte[].class), anyLong(),
                anyInt(), anyBoolean(),
                nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                anyInt(), anyBoolean(), nullable(String.class), anyBoolean(), anyInt(),
                anyInt());
        doReturn(mInboundSmsTracker).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(Context.class), nullable(Cursor.class), anyBoolean());

        mContentProvider = new FakeSmsContentProvider();
        ((MockContentResolver)mContext.getContentResolver()).addProvider(
                Telephony.Sms.CONTENT_URI.getAuthority(), mContentProvider);

        mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(mContext,
            mSmsStorageMonitor, mPhone, null);
        monitorTestableLooper(new TestableLooper(mCdmaInboundSmsHandler.getHandler().getLooper()));
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        // wait for wakelock to be released; timeout at 10s
        int i = 0;
        while (mCdmaInboundSmsHandler.getWakeLock().isHeld() && i < 100) {
            processAllMessages();
            waitForMs(100);
            i++;
        }
        assertFalse(mCdmaInboundSmsHandler.getWakeLock().isHeld());
        mCdmaInboundSmsHandler.quit();
        mCdmaInboundSmsHandler = null;
        mContentProvider.shutdown();
        mContentProvider = null;
        mSmsEnvelope = null;
        mInboundSmsTracker = null;
        super.tearDown();
    }

    private void transitionFromStartupToIdle() {
        // verify initially in StartupState
        assertEquals("StartupState", getCurrentState().getName());

        // trigger transition to IdleState
        mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_START_ACCEPTING_SMS);
        processAllMessages();

        assertEquals("IdleState", getCurrentState().getName());
    }

    @FlakyTest
    @Test
    @MediumTest
    @Ignore
    public void testNewSms() {
        transitionFromStartupToIdle();

        // send new SMS to state machine and verify that triggers SMS_DELIVER_ACTION
        doReturn(SmsEnvelope.TELESERVICE_WMT).when(mCdmaSmsMessage).getTeleService();
        mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        processAllMessages();

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getValue().getAction());

        // verify a message id was created on receive.
        assertNotEquals(0L,
                intentArgumentCaptor.getValue().getLongExtra("messageId", 0L));
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(1).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        processAllMessages();

        assertEquals("IdleState", getCurrentState().getName());
    }

    @FlakyTest /* flakes 0.43% of the time */
    @Test
    @MediumTest
    public void testNewSmsFromBlockedNumber_noBroadcastsSent() {
        String blockedNumber = "123456789";
        mInboundSmsTracker = new InboundSmsTracker(
                mContext,
                mSmsPdu, /* pdu */
                System.currentTimeMillis(), /* timestamp */
                -1, /* destPort */
                true, /* is3gpp2 */
                false, /* is3gpp2WapPdu */
                "1234567890", /* address */
                blockedNumber, /* displayAddress */
                "This is the message body of a single-part message" /* messageBody */,
                false, /* isClass0 */
                mSubId0,
                InboundSmsHandler.SOURCE_NOT_INJECTED);
        doReturn(mInboundSmsTracker).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(Context.class), nullable(byte[].class), anyLong(),
                anyInt(), anyBoolean(),
                anyBoolean(), nullable(String.class), nullable(String.class),
                nullable(String.class), anyBoolean(), anyInt(), anyInt());
        mFakeBlockedNumberContentProvider.mBlockedNumbers.add(blockedNumber);

        transitionFromStartupToIdle();

        doReturn(SmsEnvelope.TELESERVICE_WMT).when(mCdmaSmsMessage).getTeleService();
        mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        processAllMessages();

        verify(mContext, never()).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testCtWdpParsing() {
        transitionFromStartupToIdle();
        String pdu = "000000000000FDEA00000000000000000100000000000000000000001900031" +
                "040900112488ea794e074d69e1b7392c270326cde9e98";
        SmsMessage msg = SmsMessage.createFromPdu(HexDump.hexStringToByteArray(pdu));
        mSmsMessage.mWrappedSmsMessage = msg;
        mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(200);
        assertEquals(msg.getTeleService(), SmsEnvelope.TELESERVICE_FDEA_WAP);
        assertEquals("Test standard SMS", msg.getMessageBody());
        assertNotNull(msg.getUserData());
    }
}
