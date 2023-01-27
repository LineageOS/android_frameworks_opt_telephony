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

package com.android.internal.telephony.cat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.UserHandle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.SmsController;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CATServiceTest extends TelephonyTest {

    private static final String SMS_SENT_ACTION =
            "com.android.internal.telephony.cat.SMS_SENT_ACTION";
    private static final String SMS_DELIVERY_ACTION =
            "com.android.internal.telephony.cat.SMS_DELIVERY_ACTION";
    //Mocked Classes
    @Mock
    private RilMessageDecoder mRilMessageDecoder;
    private IccFileHandler mIccFileHandler;
    private SmsController mSmsController;
    private CommandDetails mCommandDetails;
    private CatService mCatService;
    private IccCardStatus mIccCardStatus;
    private IccIoResult mIccIoResult;
    private String mData =
            "D059810301130082028183051353656E64696E672072657175657374202E2E2E0607911989548056780B"
                    + "3051FF05812143F500F6082502700000201115001500BFFF01BA23C2169EA9B02D7A7FBAA0"
                    + "DAABFEE8B8DE9DA06DCD234E";
    private byte[] mRawdata = IccUtils.hexStringToBytes(mData);
    private List<ComprehensionTlv> mCtlvs;

    /**
     * Terminal Response with result code in last 3 bytes = length + SMS_RP_ERROR(0x35)
     * + ErrorCode(= 41)
     */
    private String mTerminalResponseForSmsRpError = "81030113000202828183023529";

    /**
     * Terminal Response with result code in last 3 bytes = length + NETWORK_UNABLE_TO_PROCESS(0x21)
     * + ErrorCode(= 41 with 8th bit set to 1)
     */
    private String mTerminalResponseForNetworkUnableToProcess = "810301130002028281830221A9";

    /**
     * Terminal Response with result code in last 2 bytes = length
     * + TERMINAL_UNABLE_TO_PROCESS(0x20)
     */
    private String mTerminalResponseForTerminalUnableToProcess = "810301130002028281830120";

    //Terminal Response with result code(0x00)for delivery success in last 2 bytes
    private String mTerminalResponseForDeliverySuccess = "810301130002028281830100";

    public CATServiceTest() {
        super();
    }

    private IccCardApplicationStatus composeUiccApplicationStatus(
            IccCardApplicationStatus.AppType appType,
            IccCardApplicationStatus.AppState appState, String aid) {
        IccCardApplicationStatus mIccCardAppStatus = new IccCardApplicationStatus();
        mIccCardAppStatus.aid = aid;
        mIccCardAppStatus.app_type = appType;
        mIccCardAppStatus.aid = aid;
        mIccCardAppStatus.app_type = appType;
        mIccCardAppStatus.app_state = appState;
        mIccCardAppStatus.pin1 = mIccCardAppStatus.pin2 =
                IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
        return mIccCardAppStatus;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mRilMessageDecoder = mock(RilMessageDecoder.class);
        mIccFileHandler = mock(IccFileHandler.class);
        mSmsController = mock(SmsController.class);
        mIccCardStatus = mock(IccCardStatus.class);
        mProxyController = mock(ProxyController.class);
        mUiccCard = mock(UiccCard.class);
        IccCardApplicationStatus umtsApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_USIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA2");
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{umtsApp};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;
        mIccIoResult = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes("FF40"));
        mSimulatedCommands = mock(SimulatedCommands.class);
        mSimulatedCommands.setIccIoResultForApduLogicalChannel(mIccIoResult);
        mUiccProfile = new UiccProfile(mContext, mSimulatedCommands, mIccCardStatus,
                0 /* phoneId */, mUiccCard, new Object());
        processAllMessages();
        logd("Created UiccProfile");
        processAllMessages();
        mCatService = CatService.getInstance(mSimulatedCommands, mContext,
                mUiccProfile, mUiccController.getSlotIdFromPhoneId(0));
        logd("Created CATService");
        createCommandDetails();
        createComprehensionTlvList();
    }

    @After
    public void tearDown() throws Exception {
        mCatService.dispose();
        mUiccProfile = null;
        mCatService = null;
        mCtlvs = null;
        mProxyController = null;
        mRilMessageDecoder = null;
        mCommandDetails = null;
        mContext = null;
        mSimulatedCommands = null;
        mIccCardStatus = null;
        mIccCard = null;
        mIccFileHandler = null;
        mIccIoResult = null;
        mSmsController = null;
        super.tearDown();
    }

    private void createCommandDetails() {
        mCommandDetails = mock(CommandDetails.class);
        mCommandDetails.compRequired = true;
        mCommandDetails.commandNumber = 1;
        mCommandDetails.typeOfCommand = 19;
        mCommandDetails.commandQualifier = 0;
    }

    private void createComprehensionTlvList() {
        ComprehensionTlv ctlv1 = new ComprehensionTlv(1, false, 3, mRawdata, 4);
        ComprehensionTlv ctlv2 = new ComprehensionTlv(2, false, 2, mRawdata, 9);
        ComprehensionTlv ctlv3 = new ComprehensionTlv(5, false, 19, mRawdata, 13);
        ComprehensionTlv ctlv4 = new ComprehensionTlv(6, false, 7, mRawdata, 34);
        ComprehensionTlv ctlv5 = new ComprehensionTlv(11, false, 48, mRawdata, 43);
        mCtlvs = new ArrayList<>();
        mCtlvs.add(ctlv1);
        mCtlvs.add(ctlv2);
        mCtlvs.add(ctlv3);
        mCtlvs.add(ctlv4);
        mCtlvs.add(ctlv5);
    }

    @Test
    public void testSendSmsCommandParams() throws Exception {
        ComprehensionTlv ctlv = new ComprehensionTlv(11, false, 48, mRawdata, 43);
        SmsMessage smsMessage = ValueParser.retrieveTpduAsSmsMessage(ctlv);
        assertNotNull(smsMessage);
        assertEquals("12345", smsMessage.getRecipientAddress());
    }

    @Test
    public void testSendSTKSmsViaCatService() {
        CommandParams cmdPrms = new CommandParams(mCommandDetails);
        when(mProxyController.getSmsController()).thenReturn(mSmsController);
        mCatService.sendStkSms("test", "12345", 1, cmdPrms, mProxyController);
        verify(mSmsController, Mockito.times(1)).sendTextForSubscriber(anyInt(),
                anyString(), nullable(String.class), anyString(), nullable(String.class),
                anyString(), Mockito.anyObject(), any(), eq(false), anyLong(), eq(true), eq(true));
    }

    @Test
    public void testprocessSMSEventNotify() throws Exception {
        CommandParamsFactory cmdPF = CommandParamsFactory.getInstance(mRilMessageDecoder,
                mIccFileHandler, mContext);
        assertEquals(false, cmdPF.processSMSEventNotify(mCommandDetails, mCtlvs));
    }

    @Test
    public void testSkipFdnCheckforSTKSmsViaCatService() {
        CommandParams cmdPrms = new CommandParams(mCommandDetails);
        when(mProxyController.getSmsController()).thenReturn(mSmsController);
        mCatService.sendStkSms("test", "12345", 1, cmdPrms, mProxyController);
        verify(mSmsController, Mockito.times(0)).isNumberBlockedByFDN(1, "12345",
                "com.android.internal.telephony");
    }

    //Create and assign a PendingResult object in BroadcastReceiver with which resultCode is updated
    private void setBroadcastReceiverPendingResult(BroadcastReceiver receiver, int resultCode) {
        BroadcastReceiver.PendingResult pendingResult =
                new BroadcastReceiver.PendingResult(resultCode,
                        "resultData",
                        /* resultExtras= */ null,
                        BroadcastReceiver.PendingResult.TYPE_UNREGISTERED,
                        /* ordered= */ true,
                        /* sticky= */ false,
                        /* token= */ null,
                        UserHandle.myUserId(),
                        /* flags= */ 0);
        receiver.setPendingResult(pendingResult);
    }

    @Test
    public void testSendTerminalResponseForSendSuccess() {
        setBroadcastReceiverPendingResult(mCatService.mSmsBroadcastReceiver, Activity.RESULT_OK);
        Intent intent = new Intent(SMS_SENT_ACTION).putExtra("cmdDetails", mCommandDetails);
        intent.putExtra("ims", true);
        mContext.sendOrderedBroadcast(intent, null, mCatService.mSmsBroadcastReceiver, null,
                Activity.RESULT_OK, null, null);
        processAllMessages();
        verify(mSimulatedCommands, never()).sendTerminalResponse(
                any(), any());
    }

    @Test
    public void testSendTerminalResponseForSendSmsRpError() {
        setBroadcastReceiverPendingResult(mCatService.mSmsBroadcastReceiver,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        Intent intent = new Intent(SMS_SENT_ACTION).putExtra("cmdDetails", mCommandDetails);
        intent.putExtra("ims", true);
        intent.putExtra("errorCode", 41);
        mContext.sendOrderedBroadcast(intent, null, mCatService.mSmsBroadcastReceiver, null,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE, null, null);
        processAllMessages();
        //Verify if the command is encoded with correct Result byte as per TS 101.267
        verify(mSimulatedCommands, atLeastOnce()).sendTerminalResponse(
                eq(mTerminalResponseForSmsRpError), any());
    }

    @Test
    public void testSendTerminalResponseForSendSmsNetworkError() {
        setBroadcastReceiverPendingResult(mCatService.mSmsBroadcastReceiver,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        Intent intent = new Intent(SMS_SENT_ACTION).putExtra("cmdDetails", mCommandDetails);
        intent.putExtra("ims", false);
        intent.putExtra("errorCode", 41);
        mContext.sendOrderedBroadcast(intent, null, mCatService.mSmsBroadcastReceiver, null,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE, null, null);
        processAllMessages();
        //Verify if the command is encoded with correct Result byte as per TS 101.267
        verify(mSimulatedCommands, atLeastOnce()).sendTerminalResponse(
                eq(mTerminalResponseForNetworkUnableToProcess), any());
    }

    @Test
    public void testSendTerminalResponseForDeliveryFailure() {
        setBroadcastReceiverPendingResult(mCatService.mSmsBroadcastReceiver,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        Intent intent = new Intent(SMS_DELIVERY_ACTION).putExtra("cmdDetails", mCommandDetails);
        mContext.sendOrderedBroadcast(intent, null, mCatService.mSmsBroadcastReceiver, null,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE, null, null);
        processAllMessages();
        //Verify if the command is encoded with correct Result byte as per TS 101.267
        verify(mSimulatedCommands, atLeastOnce()).sendTerminalResponse(
                eq(mTerminalResponseForTerminalUnableToProcess), any());
    }

    @Test
    public void testSendTerminalResponseForDeliverySuccess() {
        setBroadcastReceiverPendingResult(mCatService.mSmsBroadcastReceiver,
                Activity.RESULT_OK);
        Intent intent = new Intent(SMS_DELIVERY_ACTION).putExtra("cmdDetails", mCommandDetails);
        mContext.sendOrderedBroadcast(intent, null, mCatService.mSmsBroadcastReceiver, null,
                Activity.RESULT_OK, null, null);
        processAllMessages();
        //Verify if the command is encoded with correct Result byte as per TS 101.267
        verify(mSimulatedCommands, atLeastOnce()).sendTerminalResponse(
                eq(mTerminalResponseForDeliverySuccess), any());
    }
}
