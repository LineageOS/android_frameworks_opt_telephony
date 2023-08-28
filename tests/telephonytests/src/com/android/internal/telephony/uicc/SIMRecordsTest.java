/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;
import com.android.telephony.Rlog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SIMRecordsTest extends TelephonyTest {
    private static final List<String> SHORT_FPLMNS_LIST = Arrays.asList("12345", "123456", "09876");
    private static final List<String> LONG_FPLMNS_LIST =
            Arrays.asList("12345", "123456", "09876", "123456", "098237");
    private static final List<String> EMPTY_FPLMN_LIST = new ArrayList<>();
    private static final int EF_SIZE = 12;
    private static final int MAX_NUM_FPLMN = 4;
    private static final int SET_VOICE_MAIL_TIMEOUT = 1000;

    // Mocked classes
    private IccFileHandler mFhMock;

    private SIMRecordsUT mSIMRecordsUT;
    private TestLooper mTestLooper;
    private Handler mTestHandler;
    private SIMRecordsReceiver mSIMRecordsReceiver;

    private class SIMRecordsUT extends SIMRecords {
        SIMRecordsUT(UiccCardApplication app, Context c,
                CommandsInterface ci, IccFileHandler mFhMock) {
            super(app, c, ci);
            mFh = mFhMock;
        }
    }

    private class SIMRecordsReceiver {
        private SIMRecordsUT mSIMRecordsUT;

        public void set(SIMRecordsUT m) {
            mSIMRecordsUT = m;
        }

        public SIMRecordsUT get() {
            return mSIMRecordsUT;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mFhMock = mock(IccFileHandler.class);
        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());
        mSIMRecordsReceiver = new SIMRecordsReceiver();
        mTestHandler.post(
                () -> {
                    SIMRecordsUT mSIMRecords =
                            new SIMRecordsUT(
                                    mUiccCardApplication3gpp,
                                    mContext,
                                    mSimulatedCommands,
                                    mFhMock);
                    mSIMRecordsReceiver.set(mSIMRecords);
                });
        mTestLooper.dispatchAll();
        mSIMRecordsUT = mSIMRecordsReceiver.get();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestLooper != null) {
            mTestLooper.dispatchAll();
            mTestLooper = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
        mTestHandler = null;
        mSIMRecordsReceiver = null;
        mSIMRecordsUT = null;
        super.tearDown();
    }

    @Test
    public void testSetForbiddenPlmnsPad() {
        setUpSetForbiddenPlmnsTests();
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setForbiddenPlmns(message, SHORT_FPLMNS_LIST);
        mTestLooper.dispatchAll();

        byte[] encodedFplmn = IccUtils.encodeFplmns(SHORT_FPLMNS_LIST, EF_SIZE);
        AsyncResult ar = (AsyncResult) message.obj;
        assertEquals(SHORT_FPLMNS_LIST.size(), (int) ar.result);
        verify(mFhMock).getEFTransparentRecordSize(eq(mSIMRecordsUT.EF_FPLMN), any(Message.class));
        verify(mFhMock).updateEFTransparent(
                eq(mSIMRecordsUT.EF_FPLMN), eq(encodedFplmn), any(Message.class));
    }

    @Test
    public void testSetForbiddenPlmnsTruncate() {
        setUpSetForbiddenPlmnsTests();
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setForbiddenPlmns(message, LONG_FPLMNS_LIST);
        mTestLooper.dispatchAll();

        byte[] encodedFplmn = IccUtils.encodeFplmns(LONG_FPLMNS_LIST, EF_SIZE);
        AsyncResult ar = (AsyncResult) message.obj;
        assertEquals(MAX_NUM_FPLMN, (int) ar.result);
        verify(mFhMock).getEFTransparentRecordSize(eq(mSIMRecordsUT.EF_FPLMN), any(Message.class));
        verify(mFhMock).updateEFTransparent(
                eq(mSIMRecordsUT.EF_FPLMN), eq(encodedFplmn), any(Message.class));
    }

    @Test
    public void testSetForbiddenPlmnsClear() {
        setUpSetForbiddenPlmnsTests();
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setForbiddenPlmns(message, EMPTY_FPLMN_LIST);
        mTestLooper.dispatchAll();

        byte[] encodedFplmn = IccUtils.encodeFplmns(EMPTY_FPLMN_LIST, EF_SIZE);
        AsyncResult ar = (AsyncResult) message.obj;
        assertEquals(EMPTY_FPLMN_LIST.size(), (int) ar.result);
        verify(mFhMock).getEFTransparentRecordSize(eq(mSIMRecordsUT.EF_FPLMN), any(Message.class));
        verify(mFhMock).updateEFTransparent(
                eq(mSIMRecordsUT.EF_FPLMN), eq(encodedFplmn), any(Message.class));
    }

    private void setUpSetForbiddenPlmnsTests() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response, EF_SIZE, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .getEFTransparentRecordSize(anyInt(), any(Message.class));
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));
    }

    @Test
    public void testGetForbiddenPlmns() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    byte[] encodedFplmn = IccUtils.encodeFplmns(SHORT_FPLMNS_LIST, EF_SIZE);
                    AsyncResult.forMessage(response, encodedFplmn, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertEquals(SHORT_FPLMNS_LIST.toArray(new String[SHORT_FPLMNS_LIST.size()]),
                (String[]) ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsException() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response, null, new CommandException(
                            CommandException.Error.OPERATION_NOT_ALLOWED));
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof CommandException);
        assertTrue(((CommandException) ar.exception).getCommandError() ==
                CommandException.Error.OPERATION_NOT_ALLOWED);
        assertNull(ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsNull() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertNull(ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsEmptyList() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    byte[] encodedFplmn = IccUtils.encodeFplmns(EMPTY_FPLMN_LIST, EF_SIZE);
                    AsyncResult.forMessage(response, encodedFplmn, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertEquals(EMPTY_FPLMN_LIST.toArray(new String[EMPTY_FPLMN_LIST.size()]),
                (String[]) ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsInvalidLength() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response, new byte[]{(byte) 0xFF, (byte) 0xFF}, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertNull(ar.result);
    }

    @Test
    public void testGetEfPnn() {
        ArrayList<byte[]> rawPnns = new ArrayList<byte[]>();
        List<PlmnNetworkName> targetPnns = new ArrayList<PlmnNetworkName>();

        String name = "Test 1";
        rawPnns.add(encodePnn(name));
        targetPnns.add(new PlmnNetworkName(name, null));
        name = "Test 2";
        rawPnns.add(encodePnn(name));
        targetPnns.add(new PlmnNetworkName(name, null));

        Message message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_PNN_DONE);
        AsyncResult.forMessage(message, rawPnns, null);
        mSIMRecordsUT.handleMessage(message);
        List<PlmnNetworkName> parsedPnns = Arrays.asList(mSIMRecordsUT.getPnns());

        assertEquals(parsedPnns, targetPnns);
    }

    private static byte[] encodePnn(String name) {
        byte[] gsm7BitName = new byte[]{};
        try {
            gsm7BitName = GsmAlphabet.stringToGsm7BitPacked(name);
            gsm7BitName[0] = (byte) (name.length() % 8 | 0x80);
        } catch (Exception ex) {
            fail("SimRecordsTest: GsmAlphabet.stringToGsm7BitPacked() exception:" + ex);
        }
        byte[] encodedName = new byte[gsm7BitName.length + 2];
        encodedName[0] = 0x43;
        encodedName[1] = (byte) gsm7BitName.length;
        System.arraycopy(gsm7BitName, 0, encodedName, 2, gsm7BitName.length);
        return encodedName;
    }

    @Test
    public void testGetSmssTpmrValue() {
        // Testing tpmr successfully reading case
        byte[] smss = new byte[2];
        int tpmr = 10;
        smss[0] = (byte) tpmr;
        Message message = mSIMRecordsUT.obtainMessage(
                SIMRecords.EVENT_GET_SMSS_RECORD_DONE, smss);
        AsyncResult.forMessage(message, smss, null);
        mSIMRecordsUT.handleMessage(message);
        assertEquals(tpmr, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testGetSmssTpmrValueException() {
        // Testing tpmr exception case
        byte[] smss = new byte[2];
        int tpmr = 10;
        smss[0] = (byte) tpmr;
        Message message = mSIMRecordsUT.obtainMessage(
                SIMRecords.EVENT_GET_SMSS_RECORD_DONE, smss);
        AsyncResult.forMessage(message, smss, new CommandException(
                CommandException.Error.OPERATION_NOT_ALLOWED));
        mSIMRecordsUT.handleMessage(message);
        assertEquals(-1, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrValue() {
        // Testing tpmr successfully updating case
        setValidSmssValue();
        int updateTpmr = 30;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        assertEquals(updateTpmr, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrNegativevalue() {
        // Testing tpmr successfully updating case
        setValidSmssValue();
        int updateTpmr = -2;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        // 10 is previous set value
        assertEquals(10, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrHighvalue() {
        // Testing tpmr successfully updating case
        setValidSmssValue();
        int updateTpmr = 256;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        // 10 is previous set value
        assertEquals(10, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrMaxValue() {
        // Testing tpmr successfully updating case
        setValidSmssValue();
        int updateTpmr = 255;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        assertEquals(updateTpmr, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrValueException() {
        // Testing exception while setting TPMR [ with out setting initial value]
        int updateTpmr = 30;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        assertEquals(-1, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrValueException2() {
        // Testing exception while setting TPMR
        setValidSmssValue();
        int updateTpmr = 30;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);

                    AsyncResult.forMessage(response, true, new CommandException(
                            CommandException.Error.OPERATION_NOT_ALLOWED));
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof CommandException);
        assertTrue(((CommandException) ar.exception).getCommandError() ==
                CommandException.Error.OPERATION_NOT_ALLOWED);
    }

    @Test
    public void testSetSmssTpmrLargeValue() {
        // Testing Large TPMR value setting
        setValidSmssValue();
        int updateTpmr = 300;
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, true, new CommandException(
                            CommandException.Error.OPERATION_NOT_ALLOWED));
                    response.sendToTarget();
                    return null;
                })
                .when(mFhMock)
                .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof IllegalArgumentException);
    }

    private void setValidSmssValue() {
        // preset/ initialize smssvalue
        byte[] smss = new byte[2];
        int tpmr = 10;
        smss[0] = (byte) (tpmr & 0xFF);
        IccRecords.SmssRecord smssRecord = mSIMRecordsUT.createSmssRecord(null, smss);
        Message message = mSIMRecordsUT.obtainMessage(
                IsimUiccRecords.EVENT_SET_SMSS_RECORD_DONE, smssRecord);
        AsyncResult.forMessage(message, null, null);
        mSIMRecordsUT.handleMessage(message);
        assertEquals(tpmr, mSIMRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testGetEfOpl() {
        ArrayList<byte[]> rawOpl = new ArrayList<byte[]>();
        List<OperatorPlmnInfo> targetOpl = new ArrayList<OperatorPlmnInfo>();

        // OperatorPlmnInfo 1
        String plmn = "123456";
        int lacTacStart = 0x0000;
        int lacTacEnd = 0xFFFE;
        int pnnIndex = 0;

        rawOpl.add(encodeOpl(plmn, lacTacStart, lacTacEnd, pnnIndex));
        targetOpl.add(new OperatorPlmnInfo(plmn, lacTacStart, lacTacEnd, pnnIndex));

        Message message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_OPL_DONE);
        AsyncResult.forMessage(message, rawOpl, null);
        mSIMRecordsUT.handleMessage(message);
        List<OperatorPlmnInfo> parsedOpl = Arrays.asList(mSIMRecordsUT.getOpl());

        assertEquals(targetOpl, parsedOpl);

        // OperatorPlmnInfo 2
        plmn = "123DDD";
        lacTacStart = 0x0000;
        lacTacEnd = 0xFFFE;
        pnnIndex = 123;

        rawOpl.add(encodeOpl(plmn, lacTacStart, lacTacEnd, pnnIndex));
        targetOpl.add(new OperatorPlmnInfo(plmn, lacTacStart, lacTacEnd, pnnIndex));

        message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_OPL_DONE);
        AsyncResult.forMessage(message, rawOpl, null);
        mSIMRecordsUT.handleMessage(message);
        parsedOpl = Arrays.asList(mSIMRecordsUT.getOpl());

        assertEquals(targetOpl, parsedOpl);

        // OperatorPlmnInfo 3
        plmn = "123";
        lacTacStart = 0x0000;
        lacTacEnd = 0xFFFE;
        pnnIndex = 123;

        rawOpl.add(encodeOpl(plmn, lacTacStart, lacTacEnd, pnnIndex));

        message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_OPL_DONE);
        AsyncResult.forMessage(message, rawOpl, null);
        mSIMRecordsUT.handleMessage(message);
        parsedOpl = Arrays.asList(mSIMRecordsUT.getOpl());

        assertEquals(targetOpl, parsedOpl);
    }

    private byte[] encodeOpl(String plmn, int lacTacStart, int lacTacEnd, int pnnIndex) {
        byte[] data = new byte[8];

        if (plmn.length() == 5 || plmn.length() == 6) {
            IccUtils.stringToBcdPlmn(plmn, data, 0);
        } else {
            data[0] = (byte) 0xFF;
            data[1] = (byte) 0xFF;
            data[2] = (byte) 0xFF;
        }
        data[3] = (byte) (lacTacStart >>> 8);
        data[4] = (byte) lacTacStart;
        data[5] = (byte) (lacTacEnd >>> 8);
        data[6] = (byte) lacTacEnd;
        data[7] = (byte) pnnIndex;
        return data;
    }

    @Test
    public void testGetPsiSmscTelValue() {
        // Testing smsc successfully reading case
        String smscTest = "tel:+13123149810";
        String hexSmsc =
                "801074656C3A2B3133313233313439383130FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        byte[] smscBytes = getStringToByte(hexSmsc);
        Message message = mSIMRecordsUT.obtainMessage(
                SIMRecords.EVENT_GET_PSISMSC_DONE, smscBytes);
        AsyncResult.forMessage(message, smscBytes, null);
        mSIMRecordsUT.handleMessage(message);
        assertEquals(smscTest, mSIMRecordsUT.getSmscIdentity());
    }

    private byte[] getStringToByte(String hexSmsc) {
        byte[] smscBytes = IccUtils.hexStringToBytes(hexSmsc);
        return smscBytes;
    }

    @Test
    public void testGetPsiSmscSipValue() {
        // Testing smsc successfully reading case
        String smscTest = "sip:+12063130004@msg.pc.t-mobile.com;user=phone";
        byte[] smscBytes = getStringToByte(
                "802F7369703A2B3132303633313330303034406D73672E70632E742D6D6F62696C6"
                        + "52E636F6D3B757365723D70686F6E65FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        Message message = mSIMRecordsUT.obtainMessage(
                SIMRecords.EVENT_GET_PSISMSC_DONE, smscBytes);
        AsyncResult.forMessage(message, smscBytes, null);
        mSIMRecordsUT.handleMessage(message);
        assertEquals(smscTest, mSIMRecordsUT.getSmscIdentity());
    }

    @Test
    public void testGetPsiSmscException() {
        // Testing smsc exception handling case
        String hexSmsc =
                "801074656C3A2B3133313233313439383130FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        byte[] smscBytes = getStringToByte(hexSmsc);
        Message message = mSIMRecordsUT.obtainMessage(
                SIMRecords.EVENT_GET_PSISMSC_DONE, smscBytes);
        AsyncResult ar = AsyncResult.forMessage(message, smscBytes,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mSIMRecordsUT.handleMessage(message);
        assertTrue(ar.exception instanceof CommandException);
        assertEquals(null, mSIMRecordsUT.getSmscIdentity());
    }

    @Test
    public void testGetPsiSmscValueInvalidObject() {
        // Testing smsc invalid data handling case
        String smscTest = "tel:+13123149810";
        byte[] smscBytes = GsmAlphabet.stringToGsm8BitPacked(smscTest);
        Message message = mSIMRecordsUT.obtainMessage(
                SIMRecords.EVENT_GET_PSISMSC_DONE, smscBytes);
        AsyncResult ar = AsyncResult.forMessage(message, smscBytes,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mSIMRecordsUT.handleMessage(message);
        assertEquals(null, mSIMRecordsUT.getSmscIdentity());
        assertTrue(ar.exception instanceof CommandException);
    }

    @Test
    public void testGetSimServiceTable() {
        // reading sim service table successfully case
        byte[] sst = new byte[111];
        for (int i = 0; i < sst.length; i++) {
            if (i % 2 == 0) {
                sst[i] = 0;
            } else {
                sst[i] = 1;
            }
        }
        Message message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_SST_DONE);
        AsyncResult ar = AsyncResult.forMessage(message, sst, null);
        mSIMRecordsUT.handleMessage(message);
        String mockSst = IccUtils.bytesToHexString(sst);
        String resultSst = mSIMRecordsUT.getSimServiceTable();
        assertEquals(mockSst, resultSst);
    }

    @Test
    public void testGetSimServiceTableException() {
        // sim service table exception handling case
        Message message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_SST_DONE);
        AsyncResult ar = AsyncResult.forMessage(message, null, new CommandException(
                CommandException.Error.OPERATION_NOT_ALLOWED));
        mSIMRecordsUT.handleMessage(message);
        String resultSst = mSIMRecordsUT.getSimServiceTable();
        assertEquals(null, resultSst);
    }

    @Test
    public void testGetSsimServiceTableLessTableSize() {
        // sim service table reading case
        byte[] sst = new byte[12];
        for (int i = 0; i < sst.length; i++) {
            if (i % 2 == 0) {
                sst[i] = 0;
            } else {
                sst[i] = 1;
            }
        }
        Message message = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_SST_DONE);
        AsyncResult ar = AsyncResult.forMessage(message, sst, null);
        mSIMRecordsUT.handleMessage(message);
        String mockSst = IccUtils.bytesToHexString(sst);
        String resultSst = mSIMRecordsUT.getSimServiceTable();
        assertEquals(mockSst, resultSst);
    }

    @Test
    public void testSetVoiceMailNumber() throws InterruptedException {
        String voiceMailNumber = "1234567890";
        String alphaTag = "Voicemail";
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the first invocation");
            Message response = invocation.getArgument(2);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the second invocation");
            Message response = invocation.getArgument(5);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).updateEFLinearFixed(anyInt(), eq(null), anyInt(), any(byte[].class),
                eq(null), any(Message.class));

        mSIMRecordsUT.setMailboxIndex(1);
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setVoiceMailNumber(alphaTag, voiceMailNumber, message);
        latch.await(5, TimeUnit.SECONDS);
        mTestLooper.startAutoDispatch();
        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
        verify(mFhMock, times(1)).updateEFLinearFixed(anyInt(), eq(null), anyInt(),
                any(byte[].class), eq(null), any(Message.class));
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return mSIMRecordsUT.getVoiceMailNumber() != null;
            }
        });
        assertEquals(voiceMailNumber, mSIMRecordsUT.getVoiceMailNumber());
        assertEquals(alphaTag, mSIMRecordsUT.getVoiceMailAlphaTag());
    }

    @Test
    public void testSetVoiceMailNumberBigAlphatag() throws InterruptedException {
        String voiceMailNumber = "1234567890";
        String alphaTag = "VoicemailAlphaTag-VoicemailAlphaTag";
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the first invocation");
            Message response = invocation.getArgument(2);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the second invocation");
            Message response = invocation.getArgument(5);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).updateEFLinearFixed(anyInt(), eq(null), anyInt(), any(byte[].class),
                eq(null), any(Message.class));

        mSIMRecordsUT.setMailboxIndex(1);
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setVoiceMailNumber(alphaTag, voiceMailNumber, message);
        latch.await(8, TimeUnit.SECONDS);
        mTestLooper.startAutoDispatch();
        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
        verify(mFhMock, times(1)).updateEFLinearFixed(anyInt(), eq(null), anyInt(),
                any(byte[].class), eq(null), any(Message.class));
        //if attempt to save bugAlphatag which sim don't support so we will make it null
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return mSIMRecordsUT.getVoiceMailNumber() != null;
            }
        });
        assertEquals(null, mSIMRecordsUT.getVoiceMailAlphaTag());
        assertEquals(voiceMailNumber, mSIMRecordsUT.getVoiceMailNumber());
    }

    @Test
    public void testSetVoiceMailNumberUtf16Alphatag() throws InterruptedException {
        String voiceMailNumber = "1234567890";
        String alphaTag = "หมายเลขข้อความเสียง"; // Messagerie vocale
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the first invocation");
            Message response = invocation.getArgument(2);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the second invocation");
            Message response = invocation.getArgument(5);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).updateEFLinearFixed(anyInt(), eq(null), anyInt(), any(byte[].class),
                eq(null), any(Message.class));

        mSIMRecordsUT.setMailboxIndex(1);
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setVoiceMailNumber(alphaTag, voiceMailNumber, message);
        latch.await(5, TimeUnit.SECONDS);

        mTestLooper.startAutoDispatch();
        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
        verify(mFhMock, times(1)).updateEFLinearFixed(anyInt(), eq(null), anyInt(),
                any(byte[].class), eq(null), any(Message.class));
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return mSIMRecordsUT.getVoiceMailNumber() != null;
            }
        });
        assertEquals(voiceMailNumber, mSIMRecordsUT.getVoiceMailNumber());
        //if attempt to save bugAlphatag which sim don't support so we will make it null
        assertEquals(null, mSIMRecordsUT.getVoiceMailAlphaTag());
    }

    @Test
    public void testSetVoiceMailNullNumber() throws InterruptedException {
        String voiceMailNumber = null;
        String alphaTag = "VoicemailAlphaTag"; // Messagerie vocale
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the first invocation");
            Message response = invocation.getArgument(2);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        doAnswer(invocation -> {
            int[] result = new int[3];
            result[0] = 32;
            result[1] = 32;
            result[2] = 1;
            Rlog.d("SIMRecordsTest", "Executing the second invocation");
            Message response = invocation.getArgument(5);
            AsyncResult.forMessage(response, result, null);
            response.sendToTarget();
            latch.countDown();
            return null;
        }).when(mFhMock).updateEFLinearFixed(anyInt(), eq(null), anyInt(), any(byte[].class),
                eq(null), any(Message.class));

        mSIMRecordsUT.setMailboxIndex(1);
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setVoiceMailNumber(alphaTag, voiceMailNumber, message);
        latch.await(5, TimeUnit.SECONDS);
        mTestLooper.startAutoDispatch();
        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
        verify(mFhMock, times(1)).updateEFLinearFixed(anyInt(), eq(null), anyInt(),
                any(byte[].class), eq(null), any(Message.class));
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return mSIMRecordsUT.getVoiceMailAlphaTag() != null;
            }
        });
        assertEquals(null, mSIMRecordsUT.getVoiceMailNumber());
        assertEquals(alphaTag, mSIMRecordsUT.getVoiceMailAlphaTag());
    }

    public interface Condition {
        Object expected();

        Object actual();
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            Log.d(TAG, "InterruptedException");
        }
    }

    protected void waitUntilConditionIsTrueOrTimeout(Condition condition) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start
                < (long) SIMRecordsTest.SET_VOICE_MAIL_TIMEOUT) {
            sleep(50);
        }
        assertEquals("Service Unbound", condition.expected(), condition.actual());
    }
}
