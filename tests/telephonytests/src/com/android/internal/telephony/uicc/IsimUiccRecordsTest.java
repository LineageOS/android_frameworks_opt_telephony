/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.internal.telephony.uicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class IsimUiccRecordsTest extends TelephonyTest {

    // Mocked classes
    private IccFileHandler mFhMock;
    private TestLooper mTestLooper;
    private Handler mTestHandler;
    private IsimUiccRecordsUT mIsimUiccRecordsUT;

    @SuppressWarnings("ClassCanBeStatic")
    private class IsimUiccRecordsUT extends IsimUiccRecords {
        IsimUiccRecordsUT(UiccCardApplication app, Context c,
                CommandsInterface ci, IccFileHandler mFhMock) {
            super(app, c, ci);
            mFh = mFhMock;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mFhMock = mock(IccFileHandler.class);
        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());
        mTestHandler.post(
                () -> {
                    mIsimUiccRecordsUT =
                            new IsimUiccRecordsUT(
                                    mUiccCardApplication3gpp,
                                    mContext,
                                    mSimulatedCommands,
                                    mFhMock);
                });
        mTestLooper.dispatchAll();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestLooper != null) {
            mTestLooper.dispatchAll();
            mTestLooper = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
        mTestHandler = null;
        mIsimUiccRecordsUT = null;
        super.tearDown();
    }

    @Test
    public void testBroadcastRefresh() {
        Message msg = new Message();
        msg.what = IccRecords.EVENT_REFRESH;
        msg.obj = new AsyncResult(null, null, null);
        mIsimUiccRecordsUT.handleMessage(msg);
        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCapture.capture());
        assertEquals(
                ((Intent) intentCapture.getValue()).getAction(),
                IsimUiccRecords.INTENT_ISIM_REFRESH);
    }

    @Test
    public void testPsiSmscTelValue() {
        // Testing smsc successfully reading case
        String smscTest = "tel:+13123149810";
        String hexSmsc =
                "801074656C3A2B3133313233313439383130FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        byte[] smscBytes = getStringToByte(hexSmsc);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getPsiSmscObject());
        AsyncResult.forMessage(message, smscBytes, null);
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(smscTest, mIsimUiccRecordsUT.getSmscIdentity());
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
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getPsiSmscObject());
        AsyncResult.forMessage(message, smscBytes, null);
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(smscTest, mIsimUiccRecordsUT.getSmscIdentity());
    }

    @Test
    public void testGetPsiSmscValueException() {
        // Testing smsc exception handling case
        String hexSmsc =
                "801074656C3A2B3133313233313439383130FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        byte[] smscBytes = getStringToByte(hexSmsc);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getPsiSmscObject());
        AsyncResult.forMessage(message, smscBytes,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(null, mIsimUiccRecordsUT.getSmscIdentity());
    }

    @Test
    public void testGetPsiSmscValueInvalidObject() {
        // Testing smsc invalid data handling case
        String smscTest = "tel:+13123149810";
        byte[] smscBytes = GsmAlphabet.stringToGsm8BitPacked(smscTest);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getPsiSmscObject());
        AsyncResult.forMessage(message, smscBytes,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(null, mIsimUiccRecordsUT.getSmscIdentity());
    }

    @Test
    public void testGetSmssTpmrValue() {
        // Testing tpmr successfully reading case
        byte[] smss = new byte[2];
        int tpmr = 10;
        smss[0] = (byte) (tpmr & 0xFF);
        IccRecords.SmssRecord smssRecord = mIsimUiccRecordsUT.createSmssRecord(null, smss);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IsimUiccRecords.EVENT_SET_SMSS_RECORD_DONE, smssRecord);
        AsyncResult.forMessage(message, null, null);
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(tpmr, mIsimUiccRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testGetSmssTpmrValueException() {
        // Testing tpmr reading fail case [ exception case ]
        byte[] smss = new byte[2];
        int tpmr = 10;
        smss[0] = (byte) (tpmr & 0xFF);
        IccRecords.SmssRecord smssRecord = mIsimUiccRecordsUT.createSmssRecord(null, smss);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IsimUiccRecords.EVENT_SET_SMSS_RECORD_DONE, smssRecord);
        AsyncResult.forMessage(message, null,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(-1, mIsimUiccRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testGetSmssTpmrValueExtreme() {
        // Testing extreme tpmr value case [ fails ]
        byte[] smss = new byte[2];
        int tpmr = 400;
        smss[0] = (byte) (tpmr & 0xFF);
        IccRecords.SmssRecord smssRecord = mIsimUiccRecordsUT.createSmssRecord(null, smss);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IsimUiccRecords.EVENT_SET_SMSS_RECORD_DONE, smssRecord);
        AsyncResult.forMessage(message, null, null);
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(144, mIsimUiccRecordsUT.getSmssTpmrValue());
    }

    private void setValidSmssValue() {
        // preset/ initialize smssvalue
        byte[] smss = new byte[2];
        int tpmr = 10;
        smss[0] = (byte) (tpmr & 0xFF);
        IccRecords.SmssRecord smssRecord = mIsimUiccRecordsUT.createSmssRecord(null, smss);
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IsimUiccRecords.EVENT_SET_SMSS_RECORD_DONE, smssRecord);
        AsyncResult.forMessage(message, null, null);
        mIsimUiccRecordsUT.handleMessage(message);
        assertEquals(tpmr, mIsimUiccRecordsUT.getSmssTpmrValue());
    }

    @Test
    public void testSetSmssTpmrValue() {
        // Testing tpmr successfully setting case
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
        mIsimUiccRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        assertEquals(updateTpmr, mIsimUiccRecordsUT.getSmssTpmrValue());
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
        mIsimUiccRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();
        assertEquals(-1, mIsimUiccRecordsUT.getSmssTpmrValue());
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
        mIsimUiccRecordsUT.setSmssTpmrValue(updateTpmr, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof CommandException);
        assertTrue(((CommandException) ar.exception).getCommandError() ==
                CommandException.Error.OPERATION_NOT_ALLOWED);
    }

    @Test
    public void testGetSimServiceTable() {
        // reading sim service table successfully case
        byte[] sst = new byte[9];
        for (int i = 0; i < sst.length; i++) {
            if (i % 2 == 0) {
                sst[i] = 0;
            } else {
                sst[i] = 1;
            }
        }
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getIsimIstObject());
        AsyncResult ar = AsyncResult.forMessage(message, sst, null);
        mIsimUiccRecordsUT.handleMessage(message);
        String mockSst = IccUtils.bytesToHexString(sst);
        String resultSst = mIsimUiccRecordsUT.getIsimIst();
        assertEquals(mockSst, resultSst);
    }

    @Test
    public void testGetSimServiceTableException() {
        // sim service table exception handling case
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getIsimIstObject());
        AsyncResult ar = AsyncResult.forMessage(message,  null, new CommandException(
                CommandException.Error.OPERATION_NOT_ALLOWED));
        mIsimUiccRecordsUT.handleMessage(message);
        String resultSst = mIsimUiccRecordsUT.getIsimIst();
        assertEquals(null, resultSst);
    }

    @Test
    public void testGetSsimServiceTableLessTableSize() {
        // The less IST table size will not give any problem
        byte[] sst = new byte[5];
        for (int i = 0; i < sst.length; i++) {
            if (i % 2 == 0) {
                sst[i] = 0;
            } else {
                sst[i] = 1;
            }
        }
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getIsimIstObject());
        AsyncResult ar = AsyncResult.forMessage(message, sst, null);
        mIsimUiccRecordsUT.handleMessage(message);
        String mockSst = IccUtils.bytesToHexString(sst);
        String resultSst = mIsimUiccRecordsUT.getIsimIst();
        assertEquals(mockSst, resultSst);
    }

    @Test
    public void testGetSsimServiceTableLargeTableSize() {
        // The Big IST table size will not give any problem [ in feature the table may grows]
        byte[] sst = new byte[99];
        for (int i = 0; i < sst.length; i++) {
            if (i % 2 == 0) {
                sst[i] = 0;
            } else {
                sst[i] = 1;
            }
        }
        Message message = mIsimUiccRecordsUT.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecordsUT.getIsimIstObject());
        AsyncResult ar = AsyncResult.forMessage(message, sst, null);
        mIsimUiccRecordsUT.handleMessage(message);
        String mockSst = IccUtils.bytesToHexString(sst);
        String resultSst = mIsimUiccRecordsUT.getIsimIst();
        assertEquals(mockSst, resultSst);
    }

}