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
import static org.mockito.Mockito.*;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class IsimUiccRecordsTest extends TelephonyTest {

    private IsimUiccRecords mIsimUiccRecords;

    private class IsimUiccRecordsTestHandler extends HandlerThread {
        private IsimUiccRecordsTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mIsimUiccRecords = new IsimUiccRecords(mUiccCardApplication3gpp, mContext, mSimulatedCommands);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        new IsimUiccRecordsTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mIsimUiccRecords.dispose();
        mIsimUiccRecords = null;
        super.tearDown();
    }

    @Test
    public void testBroadcastRefresh() {
        Message msg = new Message();
        msg.what = IccRecords.EVENT_REFRESH;
        msg.obj = new AsyncResult(null, null, null);
        mIsimUiccRecords.handleMessage(msg);
        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCapture.capture());

        assertEquals(
            ((Intent) intentCapture.getValue()).getAction(), IsimUiccRecords.INTENT_ISIM_REFRESH);
    }

    @Test
    public void testPsiSmscTelValue() {
        // Testing smsc successfully reading case
        String smscTest = "tel:+13123149810";
        String hexSmsc =
                "801074656C3A2B3133313233313439383130FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        byte[] smscBytes = getStringToByte(hexSmsc);
        Message message = mIsimUiccRecords.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecords.getPsiSmscObject());
        AsyncResult ar = AsyncResult.forMessage(message, smscBytes, null);
        mIsimUiccRecords.handleMessage(message);
        assertEquals(smscTest, mIsimUiccRecords.getSmscIdentity());
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
        Message message = mIsimUiccRecords.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecords.getPsiSmscObject());
        AsyncResult ar = AsyncResult.forMessage(message, smscBytes, null);
        mIsimUiccRecords.handleMessage(message);
        assertEquals(smscTest, mIsimUiccRecords.getSmscIdentity());
    }

    @Test
    public void testGetPsiSmscValueException() {
        // Testing smsc exception handling case
        String hexSmsc =
                "801074656C3A2B3133313233313439383130FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                        + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        byte[] smscBytes = getStringToByte(hexSmsc);
        Message message = mIsimUiccRecords.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecords.getPsiSmscObject());
        AsyncResult ar = AsyncResult.forMessage(message, smscBytes,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mIsimUiccRecords.handleMessage(message);
        assertEquals(null, mIsimUiccRecords.getSmscIdentity());
    }

    @Test
    public void testGetPsiSmscValueInvalidObject() {
        // Testing smsc invalid data handling case
        String smscTest = "tel:+13123149810";
        byte[] smscBytes = GsmAlphabet.stringToGsm8BitPacked(smscTest);
        Message message = mIsimUiccRecords.obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, mIsimUiccRecords.getPsiSmscObject());
        AsyncResult ar = AsyncResult.forMessage(message, smscBytes,
                new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
        mIsimUiccRecords.handleMessage(message);
        assertEquals(null, mIsimUiccRecords.getSmscIdentity());
    }
}
