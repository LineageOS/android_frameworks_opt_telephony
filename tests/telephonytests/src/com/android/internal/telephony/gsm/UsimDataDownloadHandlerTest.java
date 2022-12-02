/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.ims.stub.ImsSmsImplBase;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.ims.ImsManager;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.HexDump;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UsimDataDownloadHandlerTest extends TelephonyTest {
    // Mocked classes
    private CommandsInterface mMockCi;
    protected GsmCdmaPhone mMockPhone;
    private ImsManager mMockImsManager;
    private Resources mMockResources;

    private UsimDataDownloadHandler mUsimDataDownloadHandler;
    private Phone mPhoneObj;
    private Phone[] mPhoneslist;
    private int mPhoneId;
    private int mSlotId = 0;
    private int mToken = 0;
    private int mSmsSource = 1;
    private byte[] mTpdu;
    private byte[] mSmsAckPdu;
    //Envelope is created as per TS.131.111 for SMS-PP data downwnload operation
    private String mEnvelope = "D11502028281060591896745F306068199201269231300";
    //SMS TPDU for Class2 according to TS.123.040
    private String mPdu = "07914151551512f221110A81785634121000000666B2996C2603";

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockCi = Mockito.mock(CommandsInterface.class);
        mMockPhone = Mockito.mock(GsmCdmaPhone.class);
        mMockImsManager = Mockito.mock(ImsManager.class);
        mMockResources = Mockito.mock(Resources.class);

        mPhoneslist = new Phone[] {mMockPhone};
        mTpdu = HexDump.hexStringToByteArray(mPdu);

        //Use reflection to mock
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhoneslist);
        replaceInstance(PhoneFactory.class, "sPhone", null, mMockPhone);

        mPhoneObj = PhoneFactory.getPhone(mSlotId);
        assertNotNull(mPhoneObj);
        mPhoneId = mPhoneObj.getPhoneId();
        doReturn(mSmsStats).when(mPhoneObj).getSmsStats();

        //new UsimDataDownloadHandlerThread(TAG).start();
        //waitUntilReady();
        mUsimDataDownloadHandler = new UsimDataDownloadHandler(mMockCi, mPhoneId);
        mUsimDataDownloadHandler.setImsManager(mMockImsManager);
    }

    @After
    public void tearDown() throws Exception {
        mUsimDataDownloadHandler = null;
        mPhoneslist = null;
        super.tearDown();
    }

    @Test
    public void sendEnvelopeForException() throws Exception {
        setSmsPPSimConfig(false);
        com.android.internal.telephony.gsm.SmsMessage sms =
                com.android.internal.telephony.gsm.SmsMessage.createFromPdu(mTpdu);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response, null, new CommandException(
                                CommandException.Error.OPERATION_NOT_ALLOWED));
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .sendEnvelopeWithStatus(anyString(), any(Message.class));

        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        verify(mMockCi).acknowledgeLastIncomingGsmSms(false,
                CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR, null);

        setSmsPPSimConfig(true);
        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9],
                ImsSmsImplBase.DELIVER_STATUS_ERROR_GENERIC);
    }

    @Test
    public void sendEnvelopeDataDownloadSuccess() throws Exception {
        setSmsPPSimConfig(true);
        com.android.internal.telephony.gsm.SmsMessage sms =
                com.android.internal.telephony.gsm.SmsMessage.createFromPdu(mTpdu);
        mSmsAckPdu = createSmsAckPdu(true, mEnvelope, sms);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    IccIoResult iir = new IccIoResult(0x90, 0x00,
                            IccUtils.hexStringToBytes(mEnvelope));
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .sendEnvelopeWithStatus(anyString(), any(Message.class));

        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9],
                ImsSmsImplBase.DELIVER_STATUS_OK, mSmsAckPdu);

        setSmsPPSimConfig(false);
        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        verify(mMockCi).acknowledgeIncomingGsmSmsWithPdu(true,
                IccUtils.bytesToHexString(mSmsAckPdu), null);
    }

    @Test
    public void sendEnvelopeDataDownloadFailed() throws Exception {
        setSmsPPSimConfig(false);
        com.android.internal.telephony.gsm.SmsMessage sms =
                com.android.internal.telephony.gsm.SmsMessage.createFromPdu(mTpdu);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    IccIoResult iir = new IccIoResult(0x93, 0x00,
                            IccUtils.hexStringToBytes(mEnvelope));
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .sendEnvelopeWithStatus(anyString(), any(Message.class));

        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        verify(mMockCi).acknowledgeLastIncomingGsmSms(false,
                CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY, null);

        setSmsPPSimConfig(true);
        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9],
                ImsSmsImplBase.DELIVER_STATUS_ERROR_GENERIC);
    }

    @Test
    public void sendEnvelopeForSw1_62() throws Exception {
        setSmsPPSimConfig(false);
        com.android.internal.telephony.gsm.SmsMessage sms =
                com.android.internal.telephony.gsm.SmsMessage.createFromPdu(mTpdu);
        mSmsAckPdu = createSmsAckPdu(false, mEnvelope, sms);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    IccIoResult iir = new IccIoResult(0x62, 0x63,
                            IccUtils.hexStringToBytes(mEnvelope));
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .sendEnvelopeWithStatus(anyString(), any(Message.class));

        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        verify(mMockCi).acknowledgeIncomingGsmSmsWithPdu(false,
                IccUtils.bytesToHexString(mSmsAckPdu), null);

        setSmsPPSimConfig(true);
        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9],
                ImsSmsImplBase.DELIVER_STATUS_OK, mSmsAckPdu);
    }

    @Test
    public void smsCompleteForException() throws Exception {
        setSmsPPSimConfig(false);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(3);
                    AsyncResult.forMessage(response, null, new CommandException(
                                CommandException.Error.OPERATION_NOT_ALLOWED));
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .writeSmsToSim(anyInt(), anyString(), anyString(), any(Message.class));

        int[] responseInfo = {mSmsSource, mTpdu[9], mToken};
        Message msg = mUsimDataDownloadHandler.obtainMessage(3 /* EVENT_WRITE_SMS_COMPLETE */,
                responseInfo);
        AsyncResult.forMessage(msg, null, new CommandException(
                                CommandException.Error.OPERATION_NOT_ALLOWED));
        mUsimDataDownloadHandler.handleMessage(msg);
        verify(mMockCi).acknowledgeLastIncomingGsmSms(false,
                CommandsInterface.GSM_SMS_FAIL_CAUSE_UNSPECIFIED_ERROR, null);

        setSmsPPSimConfig(true);
        mUsimDataDownloadHandler.handleMessage(msg);
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9],
                ImsSmsImplBase.DELIVER_STATUS_ERROR_GENERIC);
    }

    @Test
    public void smsComplete() throws Exception {
        setSmsPPSimConfig(true);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(3);
                    IccIoResult iir = new IccIoResult(0x90, 0x00,
                            IccUtils.hexStringToBytes(mEnvelope));
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .writeSmsToSim(anyInt(), anyString(), anyString(), any(Message.class));

        int[] responseInfo = {mSmsSource, mTpdu[9], mToken};
        Message msg = mUsimDataDownloadHandler.obtainMessage(3 /* EVENT_WRITE_SMS_COMPLETE */,
                responseInfo);
        AsyncResult.forMessage(msg, null, null);
        mUsimDataDownloadHandler.handleMessage(msg);
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9], ImsSmsImplBase.DELIVER_STATUS_OK);

        setSmsPPSimConfig(false);
        mUsimDataDownloadHandler.handleMessage(msg);
        verify(mMockCi).acknowledgeLastIncomingGsmSms(true, 0, null);
    }

    @Test
    public void failureEnvelopeResponse() throws Exception {
        setSmsPPSimConfig(false);
        com.android.internal.telephony.gsm.SmsMessage sms =
                com.android.internal.telephony.gsm.SmsMessage.createFromPdu(mTpdu);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    IccIoResult iir = new IccIoResult(0x62, 0x63,
                            IccUtils.hexStringToBytes(null)); //ForNullResponseBytes
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .sendEnvelopeWithStatus(anyString(), any(Message.class));

        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        verify(mMockCi).acknowledgeLastIncomingGsmSms(false,
                CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR, null);

        setSmsPPSimConfig(true);
        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9],
                ImsSmsImplBase.DELIVER_STATUS_ERROR_GENERIC);
    }

    @Test
    public void successEnvelopeResponse() throws Exception {
        setSmsPPSimConfig(false);
        com.android.internal.telephony.gsm.SmsMessage sms =
                com.android.internal.telephony.gsm.SmsMessage.createFromPdu(mTpdu);

        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    IccIoResult iir = new IccIoResult(0x90, 0x00,
                            IccUtils.hexStringToBytes(null)); //ForNullResponseBytes
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mMockCi)
                .sendEnvelopeWithStatus(anyString(), any(Message.class));

        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        verify(mMockCi).acknowledgeLastIncomingGsmSms(true, 0, null);

        setSmsPPSimConfig(true);
        mUsimDataDownloadHandler.startDataDownload(sms,
                 InboundSmsHandler.SOURCE_INJECTED_FROM_IMS, mToken);
        processAllMessages();
        //mTpdu[9] holds messageReference value
        verify(mMockImsManager).acknowledgeSms(mToken, mTpdu[9], ImsSmsImplBase.DELIVER_STATUS_OK);
    }

    //To set "config_smppsim_response_via_ims" for testing purpose
    private void setSmsPPSimConfig(boolean config) {
        mUsimDataDownloadHandler.setResourcesForTest(mMockResources);
        doReturn(config).when(mMockResources).getBoolean(
                com.android.internal.R.bool.config_smppsim_response_via_ims);
    }

    private byte[] createSmsAckPdu(boolean success, String envelope, SmsMessage smsMessage) {
        byte[] responseBytes = IccUtils.hexStringToBytes(envelope);
        int dcs = 0x00;
        int pid = smsMessage.getProtocolIdentifier();
        byte[] smsAckPdu;
        int index = 0;
        if (success) {
            smsAckPdu = new byte[responseBytes.length + 5];
            smsAckPdu[index++] = 0x00;
            smsAckPdu[index++] = 0x07;
        } else {
            smsAckPdu = new byte[responseBytes.length + 6];
            smsAckPdu[index++] = 0x00;
            smsAckPdu[index++] = (byte)
                    CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR;
            smsAckPdu[index++] = 0x07;
        }

        smsAckPdu[index++] = (byte) pid;
        smsAckPdu[index++] = (byte) dcs;

        if (((dcs & 0x8C) == 0x00) || ((dcs & 0xF4) == 0xF0)) {
            int septetCount = responseBytes.length * 8 / 7;
            smsAckPdu[index++] = (byte) septetCount;
        } else {
            smsAckPdu[index++] = (byte) responseBytes.length;
        }

        System.arraycopy(responseBytes, 0, smsAckPdu, index, responseBytes.length);
        return smsAckPdu;
    }
}
