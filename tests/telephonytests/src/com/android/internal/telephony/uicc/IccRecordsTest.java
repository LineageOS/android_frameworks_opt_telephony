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

import static com.android.internal.telephony.uicc.IccRecords.EVENT_APP_DETECTED;
import static com.android.internal.telephony.uicc.IccRecords.EVENT_APP_READY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class IccRecordsTest extends TelephonyTest {

    private IccRecords mIccRecords;

    private class IccRecordsTestHandler extends HandlerThread {
        private IccRecordsTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mIccRecords = new SIMRecords(mUiccCardApplication3gpp, mContext, mSimulatedCommands);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        new IccRecordsTestHandler(TAG).start();
        waitUntilReady();
        verify(mUiccCardApplication3gpp).registerForReady(
                mIccRecords, EVENT_APP_READY, null);
        verify(mUiccCardApplication3gpp).registerForDetected(
                mIccRecords, EVENT_APP_DETECTED, null);
    }

    @After
    public void tearDown() throws Exception {
        mIccRecords = null;
        super.tearDown();
    }

    @Test
    public void testDisposeCallsUnregisterForIccRefresh() {
        // verify called below when IccRecords object is created
        verify(mSimulatedCommandsVerifier).registerForIccRefresh(eq(mIccRecords),
                    eq(IccRecords.EVENT_REFRESH), isNull());
        mIccRecords.dispose();
        // verify called within dispose
        verify(mSimulatedCommandsVerifier).unregisterForIccRefresh(eq(mIccRecords));

    }

    @Test
    public void testSetImsiInvalid() {
        mIccRecords.setImsi("0123456789FFFFFF");
        assertEquals(mIccRecords.getIMSI(), "0123456789");
        mIccRecords.setImsi("0123456789ffffff");
        assertEquals(mIccRecords.getIMSI(), "0123456789");
        mIccRecords.setImsi("ffffff");
        assertEquals(mIccRecords.getIMSI(), null);
        mIccRecords.setImsi("12F34F567890");
        assertEquals(mIccRecords.getIMSI(), null);
        mIccRecords.setImsi("123456ABCDEF");
        assertEquals(mIccRecords.getIMSI(), null);
    }

    @Test
    public void testPendingTansaction() {
        Message msg = Message.obtain();
        Object obj = new Object();
        int key = mIccRecords.storePendingTransaction(msg, obj);
        Pair<Message, Object> pair = mIccRecords.retrievePendingTransaction(key);
        assertEquals(msg, pair.first);
        assertEquals(obj, pair.second);
        pair = mIccRecords.retrievePendingTransaction(key);
        assertNull(pair);
    }

    @Test
    public void testGetSmsCapacityOnIcc() {
        // set the number of records to 500
        int[] records = new int[3];
        records[2] = 500;
        Message fetchCapacityDone = mIccRecords.obtainMessage(
                IccRecords.EVENT_GET_SMS_RECORD_SIZE_DONE);
        AsyncResult.forMessage(fetchCapacityDone, records, null);
        fetchCapacityDone.sendToTarget();

        // verify whether the count is 500
        waitForLastHandlerAction(mIccRecords);
        assertEquals(mIccRecords.getSmsCapacityOnIcc(), 500);
    }

    @Test
    public void testGetIccSimChallengeResponseNull() {
        long startTime;
        long timeSpent;

        // EAP-SIM rand is 16 bytes.
        String base64Challenge = "ECcTqwuo6OfY8ddFRboD9WM=";

        // Test for null result
        mSimulatedCommands.setAuthenticationMode(mSimulatedCommands.ICC_AUTHENTICATION_MODE_NULL);

        startTime = SystemClock.elapsedRealtime();
        assertNull("getIccAuthentication should return null for empty data.",
                mIccRecords.getIccSimChallengeResponse(UiccCardApplication.AUTH_CONTEXT_EAP_AKA,
                      base64Challenge));
        timeSpent = SystemClock.elapsedRealtime() - startTime;
        Log.d("IccRecordsTest", "Time (ms) for getIccSimChallengeResponse is " + timeSpent);
        assertTrue("getIccAuthentication should not timeout",
                timeSpent < mSimulatedCommands.ICC_SIM_CHALLENGE_TIMEOUT_MILLIS);
    }

    @Test
    public void testGetIccSimChallengeResponseTimeout() {
        long startTime;
        long timeSpent;

        // EAP-SIM rand is 16 bytes.
        String base64Challenge = "ECcTqwuo6OfY8ddFRboD9WM=";

        mSimulatedCommands.setAuthenticationMode(
                mSimulatedCommands.ICC_AUTHENTICATION_MODE_TIMEOUT);
        startTime = SystemClock.elapsedRealtime();
        assertNull("getIccAuthentication should return null for empty data.",
                mIccRecords.getIccSimChallengeResponse(UiccCardApplication.AUTH_CONTEXT_EAP_AKA,
                      base64Challenge));
        timeSpent = SystemClock.elapsedRealtime() - startTime;
        Log.d("IccRecordsTest", "Time (ms) for getIccSimChallengeResponse is " + timeSpent);
        assertTrue("getIccAuthentication should timeout",
                timeSpent >= mSimulatedCommands.ICC_SIM_CHALLENGE_TIMEOUT_MILLIS);
    }

    @Test
    public void testAppStateChange() {
        assertFalse(mIccRecords.isLoaded());

        mIccRecords.obtainMessage(EVENT_APP_READY).sendToTarget();
        waitForLastHandlerAction(mIccRecords);
        assertTrue(mIccRecords.isLoaded());

        mIccRecords.obtainMessage(EVENT_APP_DETECTED).sendToTarget();
        waitForLastHandlerAction(mIccRecords);
        assertFalse(mIccRecords.isLoaded());

        mIccRecords.obtainMessage(EVENT_APP_READY).sendToTarget();
        waitForLastHandlerAction(mIccRecords);
        assertTrue(mIccRecords.isLoaded());
    }

    @Test
    public void testGetIccSimChallengeResponseDefault() {
        long startTime;
        long timeSpent;

        // EAP-SIM rand is 16 bytes.
        String base64Challenge = "ECcTqwuo6OfY8ddFRboD9WM=";
        String base64Challenge2 = "EMNxjsFrPCpm+KcgCmQGnwQ=";

        // Test for default setup
        mSimulatedCommands.setAuthenticationMode(
                mSimulatedCommands.ICC_AUTHENTICATION_MODE_DEFAULT);

        // Test for null input
        startTime = SystemClock.elapsedRealtime();
        assertNull("getIccAuthentication should return null for empty data.",
                mIccRecords.getIccSimChallengeResponse(
                        UiccCardApplication.AUTH_CONTEXT_EAP_AKA, ""));
        timeSpent = SystemClock.elapsedRealtime() - startTime;
        Log.d("IccRecordsTest", "Time (ms) for getIccSimChallengeResponse is " + timeSpent);
        assertTrue("getIccAuthentication should not timeout",
                timeSpent < mSimulatedCommands.ICC_SIM_CHALLENGE_TIMEOUT_MILLIS);

        // EAP-SIM
        startTime = SystemClock.elapsedRealtime();
        String response = mIccRecords.getIccSimChallengeResponse(
                UiccCardApplication.AUTH_CONTEXT_EAP_SIM, base64Challenge);
        timeSpent = SystemClock.elapsedRealtime() - startTime;
        Log.d("IccRecordsTest", "Time (ms) for getIccSimChallengeResponse is " + timeSpent);
        Log.d("IccRecordsTest", "Result of getIccSimChallengeResponse is " + response);
        assertTrue("Response to EAP-SIM Challenge must not be Null.", response != null);

        startTime = SystemClock.elapsedRealtime();
        String response1 = mIccRecords.getIccSimChallengeResponse(
                UiccCardApplication.AUTH_CONTEXT_EAP_SIM, base64Challenge);
        timeSpent = SystemClock.elapsedRealtime() - startTime;
        Log.d("IccRecordsTest", "Time (ms) for getIccSimChallengeResponse is " + timeSpent);
        Log.d("IccRecordsTest", "Result of getIccSimChallengeResponse is " + response1);
        assertTrue("Response to EAP-SIM Challenge must be consistent.",
                response.equals(response1));

        startTime = SystemClock.elapsedRealtime();
        String response2 = mIccRecords.getIccSimChallengeResponse(
                UiccCardApplication.AUTH_CONTEXT_EAP_SIM, base64Challenge2);
        timeSpent = SystemClock.elapsedRealtime() - startTime;
        Log.d("IccRecordsTest", "Time (ms) for getIccSimChallengeResponse is " + timeSpent);
        assertTrue("Two responses must be different.", !response.equals(response2));
    }

    @Test
    public void testOperatorPlmnInfo() {
        String plmn = "123456";
        int lacTacStart = 0x0000;
        int lacTacEnd = 0xFFFE;
        int pnnIndex = 2;

        OperatorPlmnInfo opi = new OperatorPlmnInfo(plmn, lacTacStart, lacTacEnd, pnnIndex);
        assertEquals(opi.getPnnIdx(plmn, lacTacStart), pnnIndex - 1);
        assertEquals(opi.getPnnIdx(plmn, lacTacEnd), pnnIndex - 1);
        assertEquals(opi.getPnnIdx(plmn, 0xFFFF), pnnIndex - 1);
        assertEquals(opi.getPnnIdx("654321", 0xFFFF), -1);
        assertEquals(opi.getPnnIdx("12345", 0xFFFF), -1);

        lacTacStart = 0x0001;
        lacTacEnd = 0x1FFF;
        opi = new OperatorPlmnInfo(plmn, lacTacStart, lacTacEnd, pnnIndex);
        assertEquals(opi.getPnnIdx(plmn, 2), pnnIndex - 1);
        assertEquals(opi.getPnnIdx(plmn, 0x2FFF), -1);
        assertEquals(opi.getPnnIdx(plmn, 0xFFFF), -1);

        plmn = "123DDD";
        opi = new OperatorPlmnInfo(plmn, lacTacStart, lacTacEnd, pnnIndex);
        assertEquals(opi.getPnnIdx("123123", lacTacStart), pnnIndex - 1);
        assertEquals(opi.getPnnIdx("12345", lacTacStart), -1);
    }

    @Test
    public void testGetNetworkNameForPlmn() {
        // Set up PNN
        String fullName1 = "Name full 1";
        String shortName1 = "Name short 1";
        String fullName2 = "Name full 2";
        String shortName2 = "Name short 2";
        List<PlmnNetworkName> pnns = new ArrayList<PlmnNetworkName>();
        pnns.add(new PlmnNetworkName(fullName1, shortName1));
        pnns.add(new PlmnNetworkName(fullName2, shortName2));
        pnns.add(new PlmnNetworkName(null, shortName2));
        PlmnNetworkName[] pnnsArray = pnns.toArray(new PlmnNetworkName[0]);

        // Set up OPL
        String plmn1 = "345678";
        String plmn2 = "456789";
        String plmn3 = "567890";
        int lacTacStart = 0x0000;
        int lacTacEnd = 0xFFFE;
        int pnnIndex = 1;
        List<OperatorPlmnInfo> opl = new ArrayList<OperatorPlmnInfo>();
        opl.add(new OperatorPlmnInfo(plmn1, lacTacStart, lacTacEnd, pnnIndex));
        opl.add(new OperatorPlmnInfo(plmn2, lacTacStart, lacTacEnd, pnnIndex + 1));
        opl.add(new OperatorPlmnInfo(plmn3, lacTacStart, lacTacEnd, pnnIndex + 2));
        OperatorPlmnInfo[] oplArray = opl.toArray(new OperatorPlmnInfo[0]);

        // Test
        assertNull(IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray, null, 0));
        assertEquals(fullName1, IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray,
                plmn1, 0));
        assertEquals(fullName2, IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray,
                plmn2, 0));
        assertEquals(shortName2, IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray,
                plmn3, 0));
    }

    @Test
    public void testGetNetworkNameForPlmnFromPnnOpl() {
        // Set up PNN
        String fullName1 = "Name full 1";
        String shortName1 = "Name short 1";
        String fullName2 = "Name full 2";
        String shortName2 = "Name short 2";
        List<PlmnNetworkName> pnns = new ArrayList<PlmnNetworkName>();
        pnns.add(new PlmnNetworkName(fullName1, shortName1));
        pnns.add(new PlmnNetworkName(fullName2, shortName2));
        pnns.add(new PlmnNetworkName(null, shortName2));
        PlmnNetworkName[] pnnsArray = pnns.toArray(new PlmnNetworkName[0]);

        // Set up OPL
        String plmn1 = "345678";
        String plmn2 = "456789";
        String plmn3 = "567890";
        int lacTacStart = 0x0000;
        int lacTacEnd = 0xFFFE;
        int pnnIndex = 1;
        List<OperatorPlmnInfo> opl = new ArrayList<OperatorPlmnInfo>();
        opl.add(new OperatorPlmnInfo(plmn1, lacTacStart, lacTacEnd, pnnIndex));
        opl.add(new OperatorPlmnInfo(plmn2, lacTacStart, lacTacEnd, pnnIndex + 1));
        opl.add(new OperatorPlmnInfo(plmn3, lacTacStart, lacTacEnd, pnnIndex + 2));
        OperatorPlmnInfo[] oplArray = opl.toArray(new OperatorPlmnInfo[0]);

        // Test
        assertNull(IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray, null, 0));
        assertEquals(fullName1, IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray,
                plmn1, 0));
        assertEquals(fullName2, IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray,
                plmn2, 0));
        assertEquals(shortName2, IccRecords.getNetworkNameForPlmnFromPnnOpl(pnnsArray, oplArray,
                plmn3, 0));
    }
}
