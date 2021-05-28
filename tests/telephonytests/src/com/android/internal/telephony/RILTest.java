/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ALLOW_DATA;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SEND_SMS_EXPECT_MORE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CHANGE_SIM_PIN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CHANGE_SIM_PIN2;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CONFERENCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DATA_REGISTRATION_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DELETE_SMS_ON_SIM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DEVICE_IDENTITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DTMF;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENABLE_UICC_APPLICATIONS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PIN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PIN2;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PUK;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PUK2;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_ACTIVITY_INFO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_BARRING_INFO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_CELL_INFO_LIST;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_CURRENT_CALLS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_HARDWARE_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_IMSI;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_RADIO_CAPABILITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SIM_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SLICING_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SMSC_ADDRESS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_UICC_APPLICATIONS_ENABLEMENT;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_HANGUP;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_IMS_REGISTRATION_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_IMS_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_LAST_CALL_FAIL_CAUSE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_READ_ITEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_RESET_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_WRITE_ITEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_OPERATOR;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_PULL_LCEDATA;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_REPORT_SMS_MEMORY_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_DEVICE_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_SMS_EXPECT_MORE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SETUP_DATA_CALL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_INITIAL_ATTACH_APN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SIM_CARD_POWER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SMSC_ADDRESS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SHUTDOWN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIGNAL_STRENGTH;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_AUTHENTICATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_CLOSE_CHANNEL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_OPEN_CHANNEL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_START_LCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_START_NETWORK_SCAN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STOP_LCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_UDUB;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_VOICE_RADIO_TECH;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_VOICE_REGISTRATION_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_WRITE_SMS_TO_SIM;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.hardware.radio.V1_0.Carrier;
import android.hardware.radio.V1_0.CdmaSmsMessage;
import android.hardware.radio.V1_0.DataProfileInfo;
import android.hardware.radio.V1_0.GsmSmsMessage;
import android.hardware.radio.V1_0.ImsSmsMessage;
import android.hardware.radio.V1_0.NvWriteItem;
import android.hardware.radio.V1_0.RadioError;
import android.hardware.radio.V1_0.RadioResponseInfo;
import android.hardware.radio.V1_0.RadioResponseType;
import android.hardware.radio.V1_0.RadioTechnologyFamily;
import android.hardware.radio.V1_0.SmsWriteArgs;
import android.hardware.radio.V1_6.IRadio;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.WorkSource;
import android.service.carrier.CarrierIdentifier;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessFamily;
import android.telephony.RadioAccessSpecifier;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.EpsQos;
import android.telephony.data.QosBearerFilter;
import android.telephony.data.QosBearerSession;
import android.telephony.data.TrafficDescriptor;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.dataconnection.DcTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class RILTest extends TelephonyTest {

    // refer to RIL#DEFAULT_BLOCKING_MESSAGE_RESPONSE_TIMEOUT_MS
    private static final int DEFAULT_BLOCKING_MESSAGE_RESPONSE_TIMEOUT_MS = 2000;

    // refer to RIL#DEFAULT_WAKE_LOCK_TIMEOUT_MS
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT_MS = 60000;

    @Mock
    private ConnectivityManager mConnectionManager;
    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private IRadio mRadioProxy;

    private HalVersion mRadioVersionV10 = new HalVersion(1, 0);
    private HalVersion mRadioVersionV11 = new HalVersion(1, 1);
    private HalVersion mRadioVersionV12 = new HalVersion(1, 2);
    private HalVersion mRadioVersionV13 = new HalVersion(1, 3);
    private HalVersion mRadioVersionV14 = new HalVersion(1, 4);
    private HalVersion mRadioVersionV15 = new HalVersion(1, 5);
    private HalVersion mRadioVersionV16 = new HalVersion(1, 6);

    private RIL mRILInstance;
    private RIL mRILUnderTest;
    ArgumentCaptor<Integer> mSerialNumberCaptor = ArgumentCaptor.forClass(Integer.class);

    // Constants
    private static final String ALPHA_LONG = "long";
    private static final String ALPHA_SHORT = "short";
    private static final int ARFCN = 690;
    private static final int BASESTATION_ID = 65531;
    private static final int BIT_ERROR_RATE = 99;
    private static final int BSIC = 8;
    private static final int CI = 268435456;
    private static final int CID = 65535;
    private static final int CQI = 2147483647;
    private static final int DBM = -74;
    private static final int EARFCN = 262140;
    private static final List<Integer> BANDS = Arrays.asList(1, 2);
    private static final int BANDWIDTH = 5000;
    private static final int ECIO = -124;
    private static final String EMPTY_ALPHA_LONG = "";
    private static final String EMPTY_ALPHA_SHORT = "";
    private static final int LAC = 65535;
    private static final int LATITUDE = 1292000;
    private static final int LONGITUDE = 1295000;
    private static final int MCC = 120;
    private static final String MCC_STR = "120";
    private static final int MNC = 260;
    private static final String MNC_STR = "260";
    private static final int NETWORK_ID = 65534;
    private static final int NRARFCN = 3279165;
    private static final int PCI = 503;
    private static final int PSC = 500;
    private static final int RIL_TIMESTAMP_TYPE_OEM_RIL = 3;
    private static final int RSSNR = CellInfo.UNAVAILABLE;
    private static final int RSRP = -96;
    private static final int RSRQ = -10;
    private static final int RSCP = -94;
    private static final int RSCP_ASU = 26;
    private static final int ECNO = -21;
    private static final int ECNO_ASU = 6;
    private static final int SIGNAL_NOISE_RATIO = 6;
    private static final int RSSI = -65;
    private static final int RSSI_ASU = 24;
    private static final int SYSTEM_ID = 65533;
    private static final int TAC = 65535;
    private static final int TIMING_ADVANCE = 4;
    private static final long TIMESTAMP = 215924934;
    private static final int UARFCN = 690;
    private static final int TYPE_CDMA = 2;
    private static final int TYPE_GSM = 1;
    private static final int TYPE_LTE = 3;
    private static final int TYPE_WCDMA = 4;
    private static final int TYPE_TD_SCDMA = 5;

    private static final int PROFILE_ID = 0;
    private static final String APN = "apn";
    private static final int PROTOCOL = ApnSetting.PROTOCOL_IPV6;
    private static final int AUTH_TYPE = 0;
    private static final String USER_NAME = "username";
    private static final String PASSWORD = "password";
    private static final int TYPE = 0;
    private static final int MAX_CONNS_TIME = 1;
    private static final int MAX_CONNS = 3;
    private static final int WAIT_TIME = 10;
    private static final boolean APN_ENABLED = true;
    private static final int SUPPORTED_APN_TYPES_BITMASK = 123456;
    private static final int ROAMING_PROTOCOL = ApnSetting.PROTOCOL_IPV6;
    private static final int BEARER_BITMASK = 123123;
    private static final int MTU = 1234;
    private static final boolean PERSISTENT = true;

    private static final String[] ADDITIONAL_PLMNS = new String[] {"00101", "001001", "12345"};

    private static final boolean CSG_INDICATION = true;
    private static final String HOME_NODEB_NAME = "Android Network";
    private static final int CSG_IDENTITY = 0xC0FFEE;

    @Before
    public void setUp() throws Exception {
        super.setUp(RILTest.class.getSimpleName());
        try {
            TelephonyDevController.create();
        } catch (RuntimeException e) {
        }
        Context context = new ContextFixture().getTestDouble();
        doReturn(true).when(mConnectionManager)
            .isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
        doReturn(mConnectionManager).when(context)
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        doReturn(mTelephonyManager).when(context)
                .getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(true).when(mTelephonyManager).isDataCapable();
        PowerManager powerManager = new PowerManager(context, mock(IPowerManager.class),
                mock(IThermalService.class), new Handler(Looper.myLooper()));
        doReturn(powerManager).when(context).getSystemService(Context.POWER_SERVICE);
        doReturn(new ApplicationInfo()).when(context).getApplicationInfo();

        mRILInstance = new RIL(context,
                RadioAccessFamily.getRafFromNetworkType(RILConstants.PREFERRED_NETWORK_MODE),
            Phone.PREFERRED_CDMA_SUBSCRIPTION, 0);
        mRILUnderTest = spy(mRILInstance);
        doReturn(mRadioProxy).when(mRILUnderTest).getRadioProxy(any());

        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV10);
        } catch (Exception e) {
        }
    }

    @After
    public void tearDown() throws Exception {
        mRILUnderTest.mWakeLock.release();
        mRILUnderTest.mAckWakeLock.release();
        super.tearDown();
    }

    @Test
    public void testRadioErrorWithWakelockTimeout() {
        RadioBugDetector detector = mRILUnderTest.getRadioBugDetector();
        int wakelockTimeoutThreshold = detector.getWakelockTimeoutThreshold();
        for (int i = 0; i < wakelockTimeoutThreshold; i++) {
            invokeMethod(
                    mRILInstance,
                    "obtainRequest",
                    new Class<?>[]{Integer.TYPE, Message.class, WorkSource.class},
                    new Object[]{RIL_REQUEST_GET_SIM_STATUS, obtainMessage(), new WorkSource()});
        }
        moveTimeForward(DEFAULT_WAKE_LOCK_TIMEOUT_MS);
        processAllMessages();
        assertTrue(1 == detector.getWakelockTimoutCount());
    }

    @FlakyTest
    @Test
    public void testRadioErrorWithContinuousSystemErr() throws Exception {
        RadioBugDetector detector = mRILUnderTest.getRadioBugDetector();
        int systemErrorThreshold = detector.getSystemErrorThreshold();
        for (int i = 0; i < systemErrorThreshold; i++) {
            mRILUnderTest.getIccCardStatus(obtainMessage());
            verify(mRadioProxy, atLeast(1)).getIccCardStatus(mSerialNumberCaptor.capture());
            verifyRILErrorResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                    RIL_REQUEST_GET_SIM_STATUS, RadioError.SYSTEM_ERR);
        }

        int status = detector.getRadioBugStatus();
        assertTrue(status == RadioBugDetector.RADIO_BUG_REPETITIVE_SYSTEM_ERROR);
    }

    @FlakyTest
    @Test
    public void testGetIccCardStatus() throws Exception {
        mRILUnderTest.getIccCardStatus(obtainMessage());
        verify(mRadioProxy).getIccCardStatus(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_SIM_STATUS);
    }

    @FlakyTest
    @Test
    public void testSupplyIccPinForApp() throws Exception {
        String pin = "1234";
        String aid = "2345";
        mRILUnderTest.supplyIccPinForApp(pin, aid, obtainMessage());
        verify(mRadioProxy).supplyIccPinForApp(mSerialNumberCaptor.capture(), eq(pin), eq(aid));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_ENTER_SIM_PIN);
    }

    @FlakyTest
    @Test
    public void testSupplyIccPukForApp() throws Exception {
        String puk = "pukcode";
        String newPin = "1314";
        String aid = "2345";
        mRILUnderTest.supplyIccPukForApp(puk, newPin, aid, obtainMessage());
        verify(mRadioProxy)
                .supplyIccPukForApp(mSerialNumberCaptor.capture(), eq(puk), eq(newPin), eq(aid));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_ENTER_SIM_PUK);
    }

    @FlakyTest
    @Test
    public void testSupplyIccPin2ForApp() throws Exception {
        String pin = "1234";
        String aid = "2345";
        mRILUnderTest.supplyIccPin2ForApp(pin, aid, obtainMessage());
        verify(mRadioProxy).supplyIccPin2ForApp(
                mSerialNumberCaptor.capture(), eq(pin), eq(aid));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_ENTER_SIM_PIN2);
    }

    @FlakyTest
    @Test
    public void testSupplyIccPuk2ForApp() throws Exception {
        String puk = "pukcode";
        String newPin = "1314";
        String aid = "2345";
        mRILUnderTest.supplyIccPuk2ForApp(puk, newPin, aid, obtainMessage());
        verify(mRadioProxy)
                .supplyIccPuk2ForApp(mSerialNumberCaptor.capture(), eq(puk), eq(newPin), eq(aid));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_ENTER_SIM_PUK2);
    }

    @FlakyTest
    @Test
    public void testChangeIccPinForApp() throws Exception {
        String oldPin = "1234";
        String newPin = "1314";
        String aid = "2345";
        mRILUnderTest.changeIccPinForApp(oldPin, newPin, aid, obtainMessage());
        verify(mRadioProxy).changeIccPinForApp(
                mSerialNumberCaptor.capture(), eq(oldPin), eq(newPin), eq(aid));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_CHANGE_SIM_PIN);
    }

    @FlakyTest
    @Test
    public void testChangeIccPin2ForApp() throws Exception {
        String oldPin2 = "1234";
        String newPin2 = "1314";
        String aid = "2345";
        mRILUnderTest.changeIccPin2ForApp(oldPin2, newPin2, aid, obtainMessage());
        verify(mRadioProxy).changeIccPin2ForApp(
                mSerialNumberCaptor.capture(), eq(oldPin2), eq(newPin2), eq(aid));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_CHANGE_SIM_PIN2);
    }

    @FlakyTest
    @Test
    public void testSupplyNetworkDepersonalization() throws Exception {
        String netpin = "1234";
        mRILUnderTest.supplyNetworkDepersonalization(netpin, obtainMessage());
        verify(mRadioProxy).supplyNetworkDepersonalization(
                mSerialNumberCaptor.capture(), eq(netpin));
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION);
    }

    @FlakyTest
    @Test
    public void testGetCurrentCalls() throws Exception {
        mRILUnderTest.getCurrentCalls(obtainMessage());
        verify(mRadioProxy).getCurrentCalls(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_CURRENT_CALLS);
    }

    @FlakyTest
    @Test
    public void testGetIMSIForApp() throws Exception {
        String aid = "1234";
        mRILUnderTest.getIMSIForApp(aid, obtainMessage());
        verify(mRadioProxy).getImsiForApp(mSerialNumberCaptor.capture(), eq(aid));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_IMSI);
    }

    @FlakyTest
    @Test
    public void testHangupWaitingOrBackground() throws Exception {
        mRILUnderTest.hangupWaitingOrBackground(obtainMessage());
        verify(mRadioProxy).hangupWaitingOrBackground(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND);
    }

    @FlakyTest
    @Test
    public void testHangupForegroundResumeBackground() throws Exception {
        mRILUnderTest.hangupForegroundResumeBackground(obtainMessage());
        verify(mRadioProxy).hangupForegroundResumeBackground(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND);
    }

    @FlakyTest
    @Test
    public void testHangupConnection() throws Exception {
        int gsmIndex = 0;
        mRILUnderTest.hangupConnection(gsmIndex, obtainMessage());
        verify(mRadioProxy).hangup(mSerialNumberCaptor.capture(), eq(gsmIndex));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_HANGUP);
    }

    @FlakyTest
    @Test
    public void testSwitchWaitingOrHoldingAndActive() throws Exception {
        mRILUnderTest.switchWaitingOrHoldingAndActive(obtainMessage());
        verify(mRadioProxy).switchWaitingOrHoldingAndActive(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE);
    }

    @FlakyTest
    @Test
    public void testConference() throws Exception {
        mRILUnderTest.conference(obtainMessage());
        verify(mRadioProxy).conference(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_CONFERENCE);
    }

    @FlakyTest
    @Test
    public void testRejectCall() throws Exception {
        mRILUnderTest.rejectCall(obtainMessage());
        verify(mRadioProxy).rejectCall(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_UDUB);
    }

    @FlakyTest
    @Test
    public void testGetLastCallFailCause() throws Exception {
        mRILUnderTest.getLastCallFailCause(obtainMessage());
        verify(mRadioProxy).getLastCallFailCause(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_LAST_CALL_FAIL_CAUSE);
    }

    @FlakyTest
    @Test
    public void testGetSignalStrength() throws Exception {
        mRILUnderTest.getSignalStrength(obtainMessage());
        verify(mRadioProxy).getSignalStrength(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SIGNAL_STRENGTH);
    }

    @FlakyTest
    @Test
    public void testGetVoiceRegistrationState() throws Exception {
        mRILUnderTest.getVoiceRegistrationState(obtainMessage());
        verify(mRadioProxy).getVoiceRegistrationState(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_VOICE_REGISTRATION_STATE);
    }

    private RadioAccessSpecifier getRadioAccessSpecifier(CellInfo cellInfo) {
        RadioAccessSpecifier ras;
        if (cellInfo instanceof CellInfoLte) {
            int ranLte = AccessNetworkConstants.AccessNetworkType.EUTRAN;
            int[] lteChannels = {((CellInfoLte) cellInfo).getCellIdentity().getEarfcn()};
            ras = new RadioAccessSpecifier(ranLte, null /* bands */, lteChannels);
        } else if (cellInfo instanceof CellInfoWcdma) {
            int ranLte = AccessNetworkConstants.AccessNetworkType.UTRAN;
            int[] wcdmaChannels = {((CellInfoWcdma) cellInfo).getCellIdentity().getUarfcn()};
            ras = new RadioAccessSpecifier(ranLte, null /* bands */, wcdmaChannels);
        } else if (cellInfo instanceof CellInfoGsm) {
            int ranGsm = AccessNetworkConstants.AccessNetworkType.GERAN;
            int[] gsmChannels = {((CellInfoGsm) cellInfo).getCellIdentity().getArfcn()};
            ras = new RadioAccessSpecifier(ranGsm, null /* bands */, gsmChannels);
        } else {
            ras = null;
        }
        return ras;
    }

    private NetworkScanRequest getNetworkScanRequestForTesting() {
        // Construct a NetworkScanRequest for testing
        List<CellInfo> allCellInfo = mTelephonyManager.getAllCellInfo();
        List<RadioAccessSpecifier> radioAccessSpecifier = new ArrayList<>();
        for (int i = 0; i < allCellInfo.size(); i++) {
            RadioAccessSpecifier ras = getRadioAccessSpecifier(allCellInfo.get(i));
            if (ras != null) {
                radioAccessSpecifier.add(ras);
            }
        }
        if (radioAccessSpecifier.size() == 0) {
            RadioAccessSpecifier gsm = new RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.GERAN,
                    null /* bands */,
                    null /* channels */);
            radioAccessSpecifier.add(gsm);
        }
        RadioAccessSpecifier[] radioAccessSpecifierArray =
                new RadioAccessSpecifier[radioAccessSpecifier.size()];
        return new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT /* scan type */,
                radioAccessSpecifier.toArray(radioAccessSpecifierArray),
                5 /* search periodicity */,
                60 /* max search time */,
                true /*enable incremental results*/,
                5 /* incremental results periodicity */,
                null /* List of PLMN ids (MCC-MNC) */);
    }

    @FlakyTest
    @Test
    public void testStartNetworkScanWithUnsupportedResponse() throws Exception {
        // Use Radio HAL v1.5
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV15);
        } catch (Exception e) {
        }
        NetworkScanRequest nsr = getNetworkScanRequestForTesting();
        mRILUnderTest.startNetworkScan(nsr, obtainMessage());

        // Verify the v1.5 HAL methed is called firstly
        verify(mRadioProxy).startNetworkScan_1_5(mSerialNumberCaptor.capture(), any());

        // Before we find a way to trigger real RadioResponse method, emulate the behaivor.
        Consumer<RILRequest> unsupportedResponseEmulator = rr -> {
            mRILUnderTest.setCompatVersion(rr.getRequest(), RIL.RADIO_HAL_VERSION_1_4);
            mRILUnderTest.startNetworkScan(nsr, Message.obtain(rr.getResult()));
        };

        verifyRILUnsupportedResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                RIL_REQUEST_START_NETWORK_SCAN, unsupportedResponseEmulator);

        // Verify the fallback method is invoked
        verify(mRadioProxy).startNetworkScan_1_4(eq(mSerialNumberCaptor.getValue() + 1), any());
    }

    @FlakyTest
    @Test
    public void testGetVoiceRegistrationStateWithUnsupportedResponse() throws Exception {
        // Use Radio HAL v1.5
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV15);
        } catch (Exception e) {
        }
        mRILUnderTest.getVoiceRegistrationState(obtainMessage());

        // Verify the v1.5 HAL method is called
        verify(mRadioProxy).getVoiceRegistrationState_1_5(mSerialNumberCaptor.capture());

        // Before we find a way to trigger real RadioResponse method, emulate the behaivor.
        Consumer<RILRequest> unsupportedResponseEmulator = rr -> {
            mRILUnderTest.setCompatVersion(rr.getRequest(), RIL.RADIO_HAL_VERSION_1_4);
            mRILUnderTest.getVoiceRegistrationState(Message.obtain(rr.getResult()));
        };

        verifyRILUnsupportedResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                RIL_REQUEST_VOICE_REGISTRATION_STATE, unsupportedResponseEmulator);

        // Verify the fallback method is invoked
        verify(mRadioProxy).getVoiceRegistrationState(mSerialNumberCaptor.getValue() + 1);
    }

    @FlakyTest
    @Test
    public void testGetDataRegistrationState() throws Exception {
        mRILUnderTest.getDataRegistrationState(obtainMessage());
        verify(mRadioProxy).getDataRegistrationState(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_DATA_REGISTRATION_STATE);
    }

    @FlakyTest
    @Test
    public void testGetDataRegistrationStateWithUnsupportedResponse() throws Exception {
        // Use Radio HAL v1.5
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV15);
        } catch (Exception e) {
        }

        // Verify the v1.5 HAL method is called
        mRILUnderTest.getDataRegistrationState(obtainMessage());
        verify(mRadioProxy).getDataRegistrationState_1_5(mSerialNumberCaptor.capture());

        // Before we find a way to trigger real RadioResponse method, emulate the behaivor.
        Consumer<RILRequest> unsupportedResponseEmulator = rr -> {
            mRILUnderTest.setCompatVersion(rr.getRequest(), RIL.RADIO_HAL_VERSION_1_4);
            mRILUnderTest.getDataRegistrationState(Message.obtain(rr.getResult()));
        };

        verifyRILUnsupportedResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                RIL_REQUEST_DATA_REGISTRATION_STATE, unsupportedResponseEmulator);

        // Verify the fallback method is invoked
        verify(mRadioProxy).getDataRegistrationState(mSerialNumberCaptor.getValue() + 1);
    }

    @FlakyTest
    @Test
    public void testGetOperator() throws Exception {
        mRILUnderTest.getOperator(obtainMessage());
        verify(mRadioProxy).getOperator(mSerialNumberCaptor.capture());
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_OPERATOR);
    }

    @FlakyTest
    @Test
    public void testSetRadioPower() throws Exception {
        boolean on = true;
        mRILUnderTest.setRadioPower(on, obtainMessage());
        verify(mRadioProxy).setRadioPower(mSerialNumberCaptor.capture(), eq(on));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_RADIO_POWER);
    }

    @FlakyTest
    @Test
    public void testSetRadioPower_1_6() throws Exception {
        boolean on = true, forEmergencyCall = false, preferredForEmergencyCall = false;

        // Use Radio HAL v1.6
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV16);
        } catch (Exception e) {
        }

        mRILUnderTest.setRadioPower(
                on, forEmergencyCall, preferredForEmergencyCall, obtainMessage());
        verify(mRadioProxy)
                .setRadioPower_1_6(
                        mSerialNumberCaptor.capture(),
                        eq(on),
                        eq(forEmergencyCall),
                        eq(preferredForEmergencyCall));
        verifyRILResponse_1_6(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_RADIO_POWER);
    }

    @FlakyTest
    @Test
    public void testSendDtmf() throws Exception {
        char c = 'c';
        mRILUnderTest.sendDtmf(c, obtainMessage());
        verify(mRadioProxy).sendDtmf(mSerialNumberCaptor.capture(), eq(c + ""));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_DTMF);
    }

    @FlakyTest
    @Test
    public void testSendSMS() throws Exception {
        String smscPdu = "smscPdu";
        String pdu = "pdu";
        GsmSmsMessage msg = new GsmSmsMessage();
        msg.smscPdu = smscPdu;
        msg.pdu = pdu;
        mRILUnderTest.sendSMS(smscPdu, pdu, obtainMessage());
        verify(mRadioProxy).sendSms(mSerialNumberCaptor.capture(), eq(msg));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SEND_SMS);
    }

    @FlakyTest
    @Test
    public void testSendSMS_1_6() throws Exception {
        // Use Radio HAL v1.6
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV16);
        } catch (Exception e) {
        }
        String smscPdu = "smscPdu";
        String pdu = "pdu";
        GsmSmsMessage msg = new GsmSmsMessage();
        msg.smscPdu = smscPdu;
        msg.pdu = pdu;
        mRILUnderTest.sendSMS(smscPdu, pdu, obtainMessage());
        verify(mRadioProxy).sendSms_1_6(mSerialNumberCaptor.capture(), eq(msg));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SEND_SMS);
    }

    @FlakyTest
    @Test
    public void testSendSMSExpectMore() throws Exception {
        String smscPdu = "smscPdu";
        String pdu = "pdu";
        GsmSmsMessage msg = new GsmSmsMessage();
        msg.smscPdu = smscPdu;
        msg.pdu = pdu;
        mRILUnderTest.sendSMSExpectMore(smscPdu, pdu, obtainMessage());
        verify(mRadioProxy).sendSMSExpectMore(mSerialNumberCaptor.capture(), eq(msg));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SEND_SMS_EXPECT_MORE);
    }

    @FlakyTest
    @Test
    public void testSendSMSExpectMore_1_6() throws Exception {
        // Use Radio HAL v1.6
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV16);
        } catch (Exception e) {
        }
        String smscPdu = "smscPdu";
        String pdu = "pdu";
        GsmSmsMessage msg = new GsmSmsMessage();
        msg.smscPdu = smscPdu;
        msg.pdu = pdu;
        mRILUnderTest.sendSMSExpectMore(smscPdu, pdu, obtainMessage());
        verify(mRadioProxy).sendSmsExpectMore_1_6(mSerialNumberCaptor.capture(), eq(msg));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SEND_SMS_EXPECT_MORE);
    }

    @FlakyTest
    @Test
    public void testSendCdmaSMS_1_6() throws Exception {
        // Use Radio HAL v1.6
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV16);
        } catch (Exception e) {
        }
        byte[] pdu = "000010020000000000000000000000000000000000".getBytes();
        CdmaSmsMessage msg = new CdmaSmsMessage();
        constructCdmaSendSmsRilRequest(msg, pdu);
        mRILUnderTest.sendCdmaSms(pdu, obtainMessage());
        verify(mRadioProxy).sendCdmaSms_1_6(mSerialNumberCaptor.capture(), eq(msg));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_CDMA_SEND_SMS);
    }

    @FlakyTest
    @Test
    public void testSendCdmaSMSExpectMore_1_6() throws Exception {
        // Use Radio HAL v1.6
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV16);
        } catch (Exception e) {
        }
        byte[] pdu = "000010020000000000000000000000000000000000".getBytes();
        CdmaSmsMessage msg = new CdmaSmsMessage();
        constructCdmaSendSmsRilRequest(msg, pdu);
        mRILUnderTest.sendCdmaSMSExpectMore(pdu, obtainMessage());
        verify(mRadioProxy).sendCdmaSmsExpectMore_1_6(mSerialNumberCaptor.capture(), eq(msg));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                RIL_REQUEST_CDMA_SEND_SMS_EXPECT_MORE);
    }

    private void constructCdmaSendSmsRilRequest(CdmaSmsMessage msg, byte[] pdu) {
        int addrNbrOfDigits;
        int subaddrNbrOfDigits;
        int bearerDataLength;
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);

        try {
            msg.teleserviceId = dis.readInt(); // teleServiceId
            msg.isServicePresent = (byte) dis.readInt() == 1 ? true : false; // servicePresent
            msg.serviceCategory = dis.readInt(); // serviceCategory
            msg.address.digitMode = dis.read();  // address digit mode
            msg.address.numberMode = dis.read(); // address number mode
            msg.address.numberType = dis.read(); // address number type
            msg.address.numberPlan = dis.read(); // address number plan
            addrNbrOfDigits = (byte) dis.read();
            for (int i = 0; i < addrNbrOfDigits; i++) {
                msg.address.digits.add(dis.readByte()); // address_orig_bytes[i]
            }
            msg.subAddress.subaddressType = dis.read(); //subaddressType
            msg.subAddress.odd = (byte) dis.read() == 1 ? true : false; //subaddr odd
            subaddrNbrOfDigits = (byte) dis.read();
            for (int i = 0; i < subaddrNbrOfDigits; i++) {
                msg.subAddress.digits.add(dis.readByte()); //subaddr_orig_bytes[i]
            }

            bearerDataLength = dis.read();
            for (int i = 0; i < bearerDataLength; i++) {
                msg.bearerData.add(dis.readByte()); //bearerData[i]
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @FlakyTest
    @Test
    public void testWriteSmsToSim() throws Exception {
        String smscPdu = "smscPdu";
        String pdu = "pdu";
        int status = SmsManager.STATUS_ON_ICC_READ;
        SmsWriteArgs args = new SmsWriteArgs();
        args.status = 1;
        args.smsc = smscPdu;
        args.pdu = pdu;
        mRILUnderTest.writeSmsToSim(status, smscPdu, pdu, obtainMessage());
        verify(mRadioProxy).writeSmsToSim(mSerialNumberCaptor.capture(), eq(args));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_WRITE_SMS_TO_SIM);
    }

    @FlakyTest
    @Test
    public void testDeleteSmsOnSim() throws Exception {
        int index = 0;
        mRILUnderTest.deleteSmsOnSim(index, obtainMessage());
        verify(mRadioProxy).deleteSmsOnSim(mSerialNumberCaptor.capture(), eq(index));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_DELETE_SMS_ON_SIM);
    }

    @FlakyTest
    @Test
    public void testGetDeviceIdentity() throws Exception {
        mRILUnderTest.getDeviceIdentity(obtainMessage());
        verify(mRadioProxy).getDeviceIdentity(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_DEVICE_IDENTITY);
    }

    @FlakyTest
    @Test
    public void testExitEmergencyCallbackMode() throws Exception {
        mRILUnderTest.exitEmergencyCallbackMode(obtainMessage());
        verify(mRadioProxy).exitEmergencyCallbackMode(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE);
    }

    @FlakyTest
    @Test
    public void testGetSmscAddress() throws Exception {
        mRILUnderTest.getSmscAddress(obtainMessage());
        verify(mRadioProxy).getSmscAddress(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_SMSC_ADDRESS);
    }

    @FlakyTest
    @Test
    public void testSetSmscAddress() throws Exception {
        String address = "address";
        mRILUnderTest.setSmscAddress(address, obtainMessage());
        verify(mRadioProxy).setSmscAddress(mSerialNumberCaptor.capture(), eq(address));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SET_SMSC_ADDRESS);
    }

    @FlakyTest
    @Test
    public void testReportSmsMemoryStatus() throws Exception {
        boolean available = true;
        mRILUnderTest.reportSmsMemoryStatus(available, obtainMessage());
        verify(mRadioProxy).reportSmsMemoryStatus(mSerialNumberCaptor.capture(), eq(available));
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_REPORT_SMS_MEMORY_STATUS);
    }

    @FlakyTest
    @Test
    public void testReportStkServiceIsRunning() throws Exception {
        mRILUnderTest.reportStkServiceIsRunning(obtainMessage());
        verify(mRadioProxy).reportStkServiceIsRunning(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING);
    }

    @FlakyTest
    @Test
    public void testGetCdmaSubscriptionSource() throws Exception {
        mRILUnderTest.getCdmaSubscriptionSource(obtainMessage());
        verify(mRadioProxy).getCdmaSubscriptionSource(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE);
    }

    @FlakyTest
    @Test
    public void testAcknowledgeIncomingGsmSmsWithPdu() throws Exception {
        boolean success = true;
        String ackPdu = "ackPdu";
        mRILUnderTest.acknowledgeIncomingGsmSmsWithPdu(success, ackPdu, obtainMessage());
        verify(mRadioProxy).acknowledgeIncomingGsmSmsWithPdu(
                mSerialNumberCaptor.capture(), eq(success), eq(ackPdu));
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU);
    }

    @FlakyTest
    @Test
    public void testGetVoiceRadioTechnology() throws Exception {
        mRILUnderTest.getVoiceRadioTechnology(obtainMessage());
        verify(mRadioProxy).getVoiceRadioTechnology(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_VOICE_RADIO_TECH);
    }

    @FlakyTest
    @Test
    public void testGetCellInfoList() throws Exception {
        mRILUnderTest.getCellInfoList(obtainMessage(), null);
        verify(mRadioProxy).getCellInfoList(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_CELL_INFO_LIST);
    }

    @FlakyTest
    @Test
    public void testSetCellInfoListRate() throws Exception {
        int rateInMillis = 1000;
        mRILUnderTest.setCellInfoListRate(rateInMillis, obtainMessage(), null);
        verify(mRadioProxy).setCellInfoListRate(mSerialNumberCaptor.capture(), eq(rateInMillis));
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE);
    }

    @FlakyTest
    @Test
    public void testSetInitialAttachApn() throws Exception {
        ApnSetting apnSetting = ApnSetting.makeApnSetting(
                -1, "22210", "Vodafone IT", "web.omnitel.it", null, -1,
                null, null, -1, "", "", 0, ApnSetting.TYPE_DUN, ApnSetting.PROTOCOL_IP,
                ApnSetting.PROTOCOL_IP, true, 0, 0, false, 0, 0, 0, 0, -1, "");
        DataProfile dataProfile = DcTracker.createDataProfile(
                apnSetting, apnSetting.getProfileId(), false);
        boolean isRoaming = false;

        mRILUnderTest.setInitialAttachApn(dataProfile, isRoaming, obtainMessage());
        verify(mRadioProxy).setInitialAttachApn(
                mSerialNumberCaptor.capture(),
                eq((DataProfileInfo) invokeMethod(
                        mRILInstance,
                        "convertToHalDataProfile10",
                        new Class<?>[] {DataProfile.class},
                        new Object[] {dataProfile})),
                eq(dataProfile.isPersistent()),
                eq(isRoaming));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SET_INITIAL_ATTACH_APN);
    }

    @FlakyTest
    @Test
    public void testGetImsRegistrationState() throws Exception {
        mRILUnderTest.getImsRegistrationState(obtainMessage());
        verify(mRadioProxy).getImsRegistrationState(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_IMS_REGISTRATION_STATE);
    }

    @FlakyTest
    @Test
    public void testSendRetryImsGsmSms() throws Exception {
        String smscPdu = "smscPdu";
        String pdu = "pdu";
        GsmSmsMessage gsmMsg = new GsmSmsMessage();
        gsmMsg.smscPdu = smscPdu;
        gsmMsg.pdu = pdu;

        ImsSmsMessage firstMsg = new ImsSmsMessage();
        firstMsg.tech = RadioTechnologyFamily.THREE_GPP;
        firstMsg.retry = false;
        firstMsg.messageRef = 0;
        firstMsg.gsmMessage.add(gsmMsg);

        ImsSmsMessage retryMsg = new ImsSmsMessage();
        retryMsg.tech = RadioTechnologyFamily.THREE_GPP;
        retryMsg.retry = true;
        retryMsg.messageRef = 0;
        retryMsg.gsmMessage.add(gsmMsg);

        int maxRetryCount = 3;
        int firstTransmission = 0;
        for (int i = 0; i <= maxRetryCount; i++) {
            mRILUnderTest.sendImsGsmSms(smscPdu, pdu, i, 0, obtainMessage());
            if (i == firstTransmission) {
                verify(mRadioProxy, times(1)).sendImsSms(mSerialNumberCaptor.capture(),
                        eq(firstMsg));
                verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                        RIL_REQUEST_IMS_SEND_SMS);
            } else {
                verify(mRadioProxy, times(i)).sendImsSms(mSerialNumberCaptor.capture(),
                        eq(retryMsg));
                verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                        RIL_REQUEST_IMS_SEND_SMS);
            }
        }
    }

    @FlakyTest
    @Test
    public void testSendRetryImsCdmaSms() throws Exception {
        CdmaSmsMessage cdmaMsg = new CdmaSmsMessage();

        ImsSmsMessage firstMsg = new ImsSmsMessage();
        firstMsg.tech = RadioTechnologyFamily.THREE_GPP2;
        firstMsg.retry = false;
        firstMsg.messageRef = 0;
        firstMsg.cdmaMessage.add(cdmaMsg);

        ImsSmsMessage retryMsg = new ImsSmsMessage();
        retryMsg.tech = RadioTechnologyFamily.THREE_GPP2;
        retryMsg.retry = true;
        retryMsg.messageRef = 0;
        retryMsg.cdmaMessage.add(cdmaMsg);

        int maxRetryCount = 3;
        int firstTransmission = 0;
        byte pdu[] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i <= maxRetryCount; i++) {
            mRILUnderTest.sendImsCdmaSms(pdu, i, 0, obtainMessage());
            if (i == firstTransmission) {
                verify(mRadioProxy, times(1)).sendImsSms(mSerialNumberCaptor.capture(),
                        eq(firstMsg));
                verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                        RIL_REQUEST_IMS_SEND_SMS);
            } else {
                verify(mRadioProxy, times(i)).sendImsSms(mSerialNumberCaptor.capture(),
                        eq(retryMsg));
                verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                        RIL_REQUEST_IMS_SEND_SMS);
            }
        }
    }

    @FlakyTest
    @Test
    public void testIccOpenLogicalChannel() throws Exception {
        String aid = "aid";
        int p2 = 0;
        mRILUnderTest.iccOpenLogicalChannel(aid, p2, obtainMessage());
        verify(mRadioProxy).iccOpenLogicalChannel(mSerialNumberCaptor.capture(), eq(aid), eq(p2));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SIM_OPEN_CHANNEL);
    }

    @FlakyTest
    @Test
    public void testIccCloseLogicalChannel() throws Exception {
        int channel = 1;
        mRILUnderTest.iccCloseLogicalChannel(channel, obtainMessage());
        verify(mRadioProxy).iccCloseLogicalChannel(mSerialNumberCaptor.capture(), eq(channel));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SIM_CLOSE_CHANNEL);
    }

    @FlakyTest
    @Test
    public void testNvWriteItem() throws Exception {
        int itemId = 1;
        String itemValue = "value";
        mRILUnderTest.nvWriteItem(itemId, itemValue, obtainMessage(), new WorkSource());
        NvWriteItem item = new NvWriteItem();
        item.itemId = itemId;
        item.value = itemValue;
        verify(mRadioProxy).nvWriteItem(mSerialNumberCaptor.capture(), eq(item));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_NV_WRITE_ITEM);
    }

    @FlakyTest
    @Test
    public void testNvReadItem() throws Exception {
        int itemId = 1;
        mRILUnderTest.nvReadItem(itemId, obtainMessage(), new WorkSource());
        verify(mRadioProxy).nvReadItem(mSerialNumberCaptor.capture(), eq(itemId));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_NV_READ_ITEM);
    }

    @FlakyTest
    @Test
    public void testNvResetConfig() throws Exception {
        int resetType = 1;
        mRILUnderTest.nvResetConfig(resetType, obtainMessage());
        verify(mRadioProxy).nvResetConfig(
                mSerialNumberCaptor.capture(),
                eq((Integer) invokeMethod(
                        mRILInstance,
                        "convertToHalResetNvType",
                        new Class<?>[] {Integer.TYPE},
                        new Object[] {resetType})));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_NV_RESET_CONFIG);
    }

    @FlakyTest
    @Test
    public void testSetDataAllowed() throws Exception {
        boolean allowed = true;
        mRILUnderTest.setDataAllowed(allowed, obtainMessage());
        verify(mRadioProxy).setDataAllowed(mSerialNumberCaptor.capture(), eq(allowed));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_ALLOW_DATA);
    }

    @FlakyTest
    @Test
    public void testGetHardwareConfig() throws Exception {
        mRILUnderTest.getHardwareConfig(obtainMessage());
        verify(mRadioProxy).getHardwareConfig(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_HARDWARE_CONFIG);
    }

    @FlakyTest
    @Test
    public void testRequestIccSimAuthentication() throws Exception {
        int authContext = 1;
        String data = "data";
        String aid = "aid";
        mRILUnderTest.requestIccSimAuthentication(authContext, data, aid, obtainMessage());
        verify(mRadioProxy).requestIccSimAuthentication(
                mSerialNumberCaptor.capture(), eq(authContext), eq(data), eq(aid));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SIM_AUTHENTICATION);
    }

    @FlakyTest
    @Test
    public void testRequestShutdown() throws Exception {
        mRILUnderTest.requestShutdown(obtainMessage());
        verify(mRadioProxy).requestShutdown(mSerialNumberCaptor.capture());
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SHUTDOWN);
    }

    @FlakyTest
    @Test
    public void testGetRadioCapability() throws Exception {
        mRILUnderTest.getRadioCapability(obtainMessage());
        verify(mRadioProxy).getRadioCapability(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_RADIO_CAPABILITY);
    }

    @FlakyTest
    @Test
    public void testStartLceService() throws Exception {
        int reportIntervalMs = 1000;
        boolean pullMode = false;
        mRILUnderTest.startLceService(reportIntervalMs, pullMode, obtainMessage());
        verify(mRadioProxy).startLceService(
                mSerialNumberCaptor.capture(), eq(reportIntervalMs), eq(pullMode));
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_START_LCE);
    }

    @FlakyTest
    @Test
    public void testStopLceService() throws Exception {
        mRILUnderTest.stopLceService(obtainMessage());
        verify(mRadioProxy).stopLceService(mSerialNumberCaptor.capture());
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_STOP_LCE);
    }

    @FlakyTest
    @Test
    public void testPullLceData() throws Exception {
        mRILUnderTest.pullLceData(obtainMessage());
        verify(mRadioProxy).pullLceData(mSerialNumberCaptor.capture());
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_PULL_LCEDATA);
    }

    @FlakyTest
    @Test
    public void testGetModemActivityInfo() throws Exception {
        mRILUnderTest.getModemActivityInfo(obtainMessage(), new WorkSource());
        verify(mRadioProxy).getModemActivityInfo(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_ACTIVITY_INFO);
    }

    @FlakyTest
    @Test
    public void testGetModemActivityInfoTimeout() {
        mRILUnderTest.getModemActivityInfo(obtainMessage(), new WorkSource());
        assertEquals(1, mRILUnderTest.getRilRequestList().size());
        moveTimeForward(DEFAULT_BLOCKING_MESSAGE_RESPONSE_TIMEOUT_MS);
        processAllMessages();
        assertEquals(0, mRILUnderTest.getRilRequestList().size());
    }

    @FlakyTest
    @Test
    public void testSendDeviceState() throws Exception {
        int stateType = 1;
        boolean state = false;
        mRILUnderTest.sendDeviceState(stateType, state, obtainMessage());
        verify(mRadioProxy).sendDeviceState(
                mSerialNumberCaptor.capture(), eq(stateType), eq(state));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SEND_DEVICE_STATE);
    }

    @FlakyTest
    @Test
    public void testSetUnsolResponseFilter() throws Exception {
        int filter = 1;
        mRILUnderTest.setUnsolResponseFilter(filter, obtainMessage());
        verify(mRadioProxy).setIndicationFilter(mSerialNumberCaptor.capture(), eq(filter));
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER);
    }

    @FlakyTest
    @Test
    public void testSetSimCardPowerForPowerDownState() throws Exception {
        mRILUnderTest.setSimCardPower(TelephonyManager.CARD_POWER_DOWN, obtainMessage(),
                new WorkSource());
        verify(mRadioProxy).setSimCardPower(mSerialNumberCaptor.capture(), eq(false));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SET_SIM_CARD_POWER);
    }

    @FlakyTest
    @Test
    public void testSetSimCardPowerForPowerUpState() throws Exception {
        mRILUnderTest.setSimCardPower(TelephonyManager.CARD_POWER_UP, obtainMessage(),
                new WorkSource());
        verify(mRadioProxy).setSimCardPower(mSerialNumberCaptor.capture(), eq(true));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SET_SIM_CARD_POWER);
    }

    @FlakyTest
    @Test
    public void testHandleCallSetupRequestFromSim() throws Exception {
        boolean accept = true;
        mRILUnderTest.handleCallSetupRequestFromSim(accept, obtainMessage());
        verify(mRadioProxy).handleStkCallSetupRequestFromSim(
                mSerialNumberCaptor.capture(), eq(accept));
        verifyRILResponse(
                mRILUnderTest,
                mSerialNumberCaptor.getValue(),
                RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM);
    }

    @FlakyTest
    @Test
    public void testWakeLockTimeout() {
        invokeMethod(
                mRILInstance,
                "obtainRequest",
                new Class<?>[] {Integer.TYPE, Message.class, WorkSource.class},
                new Object[] {RIL_REQUEST_GET_SIM_STATUS, obtainMessage(), new WorkSource()});

        // The wake lock should be held when obtain a RIL request.
        assertTrue(mRILInstance.getWakeLock(RIL.FOR_WAKELOCK).isHeld());

        moveTimeForward(DEFAULT_WAKE_LOCK_TIMEOUT_MS);
        processAllMessages();

        // The wake lock should be released after processed the time out event.
        assertFalse(mRILInstance.getWakeLock(RIL.FOR_WAKELOCK).isHeld());
    }

    @Test
    public void testGetBarringInfo() throws Exception {
        // Not supported on Radio 1.0.
        mRILUnderTest.getBarringInfo(obtainMessage());
        verify(mRadioProxy, never()).getBarringInfo(anyInt());

        // Make radio version 1.5 to support the operation.
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV15);
        } catch (Exception e) {
        }
        mRILUnderTest.getBarringInfo(obtainMessage());
        verify(mRadioProxy).getBarringInfo(mSerialNumberCaptor.capture());
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_BARRING_INFO);
    }

    private Message obtainMessage() {
        return mRILUnderTest.getRilHandler().obtainMessage();
    }

    private static void verifyRILResponse(RIL ril, int serial, int requestType) {
        RadioResponseInfo responseInfo =
                createFakeRadioResponseInfo(serial, RadioError.NONE, RadioResponseType.SOLICITED);

        RILRequest rr = ril.processResponse(responseInfo);
        assertNotNull(rr);

        assertEquals(serial, rr.getSerial());
        assertEquals(requestType, rr.getRequest());
        assertTrue(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());

        ril.processResponseDone(rr, responseInfo, null);
        assertEquals(0, ril.getRilRequestList().size());
        assertFalse(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());
    }

    private static void verifyRILResponse_1_6(RIL ril, int serial, int requestType) {
        android.hardware.radio.V1_6.RadioResponseInfo responseInfo =
                createFakeRadioResponseInfo_1_6(
                        serial, RadioError.NONE, RadioResponseType.SOLICITED);

        RILRequest rr = ril.processResponse_1_6(responseInfo);
        assertNotNull(rr);

        assertEquals(serial, rr.getSerial());
        assertEquals(requestType, rr.getRequest());
        assertTrue(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());

        ril.processResponseDone_1_6(rr, responseInfo, null);
        assertEquals(0, ril.getRilRequestList().size());
        assertFalse(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());
    }

    private static void verifyRILErrorResponse(RIL ril, int serial, int requestType, int error) {
        RadioResponseInfo responseInfo =
                createFakeRadioResponseInfo(serial, error, RadioResponseType.SOLICITED);

        RILRequest rr = ril.processResponse(responseInfo);
        assertNotNull(rr);

        assertEquals(serial, rr.getSerial());
        assertEquals(requestType, rr.getRequest());
        assertTrue(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());

        ril.processResponseDone(rr, responseInfo, null);
        assertEquals(0, ril.getRilRequestList().size());
        assertFalse(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());
    }

    private static void verifyRILUnsupportedResponse(RIL ril, int serial, int requestType,
            Consumer<RILRequest> unsupportedResponseEmulator) {
        RadioResponseInfo responseInfo =
                createFakeRadioResponseInfo(serial, RadioError.REQUEST_NOT_SUPPORTED,
                        RadioResponseType.SOLICITED);

        RILRequest rr = ril.processResponse(responseInfo);
        assertNotNull(rr);

        assertEquals(serial, rr.getSerial());
        assertEquals(requestType, rr.getRequest());
        assertTrue(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());

        unsupportedResponseEmulator.accept(rr);

        ril.processResponseDone(rr, responseInfo, null);

        assertEquals(1, ril.getRilRequestList().size());
        assertTrue(ril.getWakeLock(RIL.FOR_WAKELOCK).isHeld());
    }

    private static RadioResponseInfo createFakeRadioResponseInfo(int serial, int error, int type) {
        RadioResponseInfo respInfo = new RadioResponseInfo();
        respInfo.serial = serial;
        respInfo.error = error;
        respInfo.type = type;
        return respInfo;
    }

    private static android.hardware.radio.V1_6.RadioResponseInfo createFakeRadioResponseInfo_1_6(
            int serial, int error, int type) {
        android.hardware.radio.V1_6.RadioResponseInfo respInfo =
                new android.hardware.radio.V1_6.RadioResponseInfo();
        respInfo.serial = serial;
        respInfo.error = error;
        respInfo.type = type;
        return respInfo;
    }

    @Test
    public void testConvertHalCellInfoListForLTE() {
        android.hardware.radio.V1_0.CellInfoLte lte = new android.hardware.radio.V1_0.CellInfoLte();
        lte.cellIdentityLte.ci = CI;
        lte.cellIdentityLte.pci = PCI;
        lte.cellIdentityLte.tac = TAC;
        lte.cellIdentityLte.earfcn = EARFCN;
        lte.cellIdentityLte.mcc = MCC_STR;
        lte.cellIdentityLte.mnc = MNC_STR;
        lte.signalStrengthLte.signalStrength = RSSI_ASU;
        lte.signalStrengthLte.rsrp = -RSRP;
        lte.signalStrengthLte.rsrq = -RSRQ;
        lte.signalStrengthLte.rssnr = RSSNR;
        lte.signalStrengthLte.cqi = CQI;
        lte.signalStrengthLte.timingAdvance = TIMING_ADVANCE;
        android.hardware.radio.V1_0.CellInfo record = new android.hardware.radio.V1_0.CellInfo();
        record.cellInfoType = TYPE_LTE;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.lte.add(lte);
        ArrayList<android.hardware.radio.V1_0.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_0.CellInfo>();
        records.add(record);

        ArrayList<CellInfo> ret = RIL.convertHalCellInfoList(records);

        assertEquals(1, ret.size());
        CellInfoLte cellInfoLte = (CellInfoLte) ret.get(0);
        CellInfoLte expected = new CellInfoLte();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityLte cil = new CellIdentityLte(CI, PCI, TAC, EARFCN, new int[] {},
                Integer.MAX_VALUE, MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthLte css = new CellSignalStrengthLte(
                RSSI, RSRP, RSRQ, RSSNR, CQI, TIMING_ADVANCE);
        expected.setCellIdentity(cil);
        expected.setCellSignalStrength(css);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_UNKNOWN);
        cellInfoLte.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoLte);
    }

    @Test
    public void testConvertHalCellInfoListForGSM() {
        android.hardware.radio.V1_0.CellInfoGsm cellinfo =
                new android.hardware.radio.V1_0.CellInfoGsm();
        cellinfo.cellIdentityGsm.lac = LAC;
        cellinfo.cellIdentityGsm.cid = CID;
        cellinfo.cellIdentityGsm.bsic = BSIC;
        cellinfo.cellIdentityGsm.arfcn = ARFCN;
        cellinfo.cellIdentityGsm.mcc = MCC_STR;
        cellinfo.cellIdentityGsm.mnc = MNC_STR;
        cellinfo.signalStrengthGsm.signalStrength = RSSI_ASU;
        cellinfo.signalStrengthGsm.bitErrorRate = BIT_ERROR_RATE;
        cellinfo.signalStrengthGsm.timingAdvance = TIMING_ADVANCE;
        android.hardware.radio.V1_0.CellInfo record = new android.hardware.radio.V1_0.CellInfo();
        record.cellInfoType = TYPE_GSM;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.gsm.add(cellinfo);
        ArrayList<android.hardware.radio.V1_0.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_0.CellInfo>();
        records.add(record);

        ArrayList<CellInfo> ret = RIL.convertHalCellInfoList(records);

        assertEquals(1, ret.size());
        CellInfoGsm cellInfoGsm = (CellInfoGsm) ret.get(0);
        CellInfoGsm expected = new CellInfoGsm();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityGsm ci = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC, MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT,
                Collections.emptyList());
        CellSignalStrengthGsm cs = new CellSignalStrengthGsm(
                RSSI, BIT_ERROR_RATE, TIMING_ADVANCE);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_UNKNOWN);
        cellInfoGsm.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoGsm);
    }

    @Test
    public void testConvertHalCellInfoListForWcdma() {
        android.hardware.radio.V1_0.CellInfoWcdma cellinfo =
                new android.hardware.radio.V1_0.CellInfoWcdma();
        cellinfo.cellIdentityWcdma.lac = LAC;
        cellinfo.cellIdentityWcdma.cid = CID;
        cellinfo.cellIdentityWcdma.psc = PSC;
        cellinfo.cellIdentityWcdma.uarfcn = UARFCN;
        cellinfo.cellIdentityWcdma.mcc = MCC_STR;
        cellinfo.cellIdentityWcdma.mnc = MNC_STR;
        cellinfo.signalStrengthWcdma.signalStrength = RSSI_ASU;
        cellinfo.signalStrengthWcdma.bitErrorRate = BIT_ERROR_RATE;
        android.hardware.radio.V1_0.CellInfo record = new android.hardware.radio.V1_0.CellInfo();
        record.cellInfoType = TYPE_WCDMA;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.wcdma.add(cellinfo);
        ArrayList<android.hardware.radio.V1_0.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_0.CellInfo>();
        records.add(record);

        ArrayList<CellInfo> ret = RIL.convertHalCellInfoList(records);

        assertEquals(1, ret.size());
        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) ret.get(0);
        CellInfoWcdma expected = new CellInfoWcdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityWcdma ci = new CellIdentityWcdma(
                LAC, CID, PSC, UARFCN, MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthWcdma cs = new CellSignalStrengthWcdma(
                RSSI, BIT_ERROR_RATE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_UNKNOWN);
        cellInfoWcdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoWcdma);
    }

    private static void initializeCellIdentityTdscdma_1_2(
            android.hardware.radio.V1_2.CellIdentityTdscdma cid) {
        cid.base.lac = LAC;
        cid.base.cid = CID;
        cid.base.cpid = PSC;
        cid.base.mcc = MCC_STR;
        cid.base.mnc = MNC_STR;
        cid.uarfcn = UARFCN;
        cid.operatorNames.alphaLong = ALPHA_LONG;
        cid.operatorNames.alphaShort = ALPHA_SHORT;
    }

    @Test
    public void testConvertHalCellInfoListForTdscdma() {
        android.hardware.radio.V1_2.CellInfoTdscdma cellinfo =
                new android.hardware.radio.V1_2.CellInfoTdscdma();
        initializeCellIdentityTdscdma_1_2(cellinfo.cellIdentityTdscdma);

        cellinfo.signalStrengthTdscdma.signalStrength = RSSI_ASU;
        cellinfo.signalStrengthTdscdma.bitErrorRate = BIT_ERROR_RATE;
        cellinfo.signalStrengthTdscdma.rscp = RSCP_ASU;
        android.hardware.radio.V1_2.CellInfo record = new android.hardware.radio.V1_2.CellInfo();
        record.cellInfoType = TYPE_TD_SCDMA;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.tdscdma.add(cellinfo);
        ArrayList<android.hardware.radio.V1_2.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_2.CellInfo>();
        records.add(record);

        ArrayList<CellInfo> ret = RIL.convertHalCellInfoList_1_2(records);

        assertEquals(1, ret.size());
        CellInfoTdscdma cellInfoTdscdma = (CellInfoTdscdma) ret.get(0);
        CellInfoTdscdma expected = new CellInfoTdscdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        CellIdentityTdscdma ci = new CellIdentityTdscdma(
                MCC_STR, MNC_STR, LAC, CID, PSC, UARFCN, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthTdscdma cs = new CellSignalStrengthTdscdma(
                RSSI, BIT_ERROR_RATE, RSCP);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        cellInfoTdscdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoTdscdma);
    }

    @Test
    public void testConvertHalCellInfoListForCdma() {
        android.hardware.radio.V1_0.CellInfoCdma cellinfo =
                new android.hardware.radio.V1_0.CellInfoCdma();
        cellinfo.cellIdentityCdma.networkId = NETWORK_ID;
        cellinfo.cellIdentityCdma.systemId = SYSTEM_ID;
        cellinfo.cellIdentityCdma.baseStationId = BASESTATION_ID;
        cellinfo.cellIdentityCdma.longitude = LONGITUDE;
        cellinfo.cellIdentityCdma.latitude = LATITUDE;
        cellinfo.signalStrengthCdma.dbm = -DBM;
        cellinfo.signalStrengthCdma.ecio = -ECIO;
        cellinfo.signalStrengthEvdo.dbm = -DBM;
        cellinfo.signalStrengthEvdo.ecio = -ECIO;
        cellinfo.signalStrengthEvdo.signalNoiseRatio = SIGNAL_NOISE_RATIO;
        android.hardware.radio.V1_0.CellInfo record = new android.hardware.radio.V1_0.CellInfo();
        record.cellInfoType = TYPE_CDMA;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.cdma.add(cellinfo);
        ArrayList<android.hardware.radio.V1_0.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_0.CellInfo>();
        records.add(record);

        ArrayList<CellInfo> ret = RIL.convertHalCellInfoList(records);

        assertEquals(1, ret.size());
        CellInfoCdma cellInfoCdma = (CellInfoCdma) ret.get(0);
        CellInfoCdma expected = new CellInfoCdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityCdma ci = new CellIdentityCdma(
                NETWORK_ID, SYSTEM_ID, BASESTATION_ID, LONGITUDE, LATITUDE,
                EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT);
        CellSignalStrengthCdma cs = new CellSignalStrengthCdma(
                DBM, ECIO, DBM, ECIO, SIGNAL_NOISE_RATIO);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_UNKNOWN);
        cellInfoCdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoCdma);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForLTE() {
        ArrayList<CellInfo> ret = getCellInfoListForLTE(MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoLte cellInfoLte = (CellInfoLte) ret.get(0);
        CellInfoLte expected = new CellInfoLte();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityLte cil = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, new int[] {}, BANDWIDTH, MCC_STR, MNC_STR,
                ALPHA_LONG, ALPHA_SHORT, Collections.emptyList(), null);
        CellSignalStrengthLte css = new CellSignalStrengthLte(
                RSSI, RSRP, RSRQ, RSSNR, CQI, TIMING_ADVANCE);
        expected.setCellIdentity(cil);
        expected.setCellSignalStrength(css);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoLte.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoLte);
    }

    @Test
    public void testConvertHalCellInfoList_1_2_ForLTEWithEmptyOperatorInfo() {
        ArrayList<CellInfo> ret = getCellInfoListForLTE(
                MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoLte cellInfoLte = (CellInfoLte) ret.get(0);
        CellInfoLte expected = new CellInfoLte();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityLte cil = new CellIdentityLte(CI, PCI, TAC, EARFCN, new int[] {},
                BANDWIDTH, MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthLte css = new CellSignalStrengthLte(
                RSSI, RSRP, RSRQ, RSSNR, CQI, TIMING_ADVANCE);
        expected.setCellIdentity(cil);
        expected.setCellSignalStrength(css);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoLte.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoLte);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForLTEWithEmptyMccMnc() {
        // MCC/MNC will be set as INT_MAX if unknown
        ArrayList<CellInfo> ret = getCellInfoListForLTE(
                String.valueOf(Integer.MAX_VALUE), String.valueOf(Integer.MAX_VALUE),
                ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoLte cellInfoLte = (CellInfoLte) ret.get(0);
        CellInfoLte expected = new CellInfoLte();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityLte cil = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, new int[] {}, BANDWIDTH, null, null, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList(), null);
        CellSignalStrengthLte css = new CellSignalStrengthLte(
                RSSI, RSRP, RSRQ, RSSNR, CQI, TIMING_ADVANCE);
        expected.setCellIdentity(cil);
        expected.setCellSignalStrength(css);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoLte.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoLte);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForGSM() {
        ArrayList<CellInfo> ret = getCellInfoListForGSM(MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoGsm cellInfoGsm = (CellInfoGsm) ret.get(0);
        CellInfoGsm expected = new CellInfoGsm();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityGsm ci = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        CellSignalStrengthGsm cs = new CellSignalStrengthGsm(
                RSSI, BIT_ERROR_RATE, TIMING_ADVANCE);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoGsm.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoGsm);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForGSMWithEmptyOperatorInfo() {
        ArrayList<CellInfo> ret = getCellInfoListForGSM(
                MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoGsm cellInfoGsm = (CellInfoGsm) ret.get(0);
        CellInfoGsm expected = new CellInfoGsm();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityGsm ci = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC, MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT,
                Collections.emptyList());
        CellSignalStrengthGsm cs = new CellSignalStrengthGsm(
                RSSI, BIT_ERROR_RATE, TIMING_ADVANCE);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoGsm.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoGsm);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForGSMWithEmptyMccMnc() {
        // MCC/MNC will be set as INT_MAX if unknown
        ArrayList<CellInfo> ret = getCellInfoListForGSM(
                String.valueOf(Integer.MAX_VALUE), String.valueOf(Integer.MAX_VALUE),
                ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoGsm cellInfoGsm = (CellInfoGsm) ret.get(0);
        CellInfoGsm expected = new CellInfoGsm();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityGsm ci = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC, null, null, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        CellSignalStrengthGsm cs = new CellSignalStrengthGsm(
                RSSI, BIT_ERROR_RATE, TIMING_ADVANCE);
        expected.setCellIdentity(ci);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        expected.setCellSignalStrength(cs);
        cellInfoGsm.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoGsm);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForWcdma() {
        ArrayList<CellInfo> ret = getCellInfoListForWcdma(
                MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) ret.get(0);
        CellInfoWcdma expected = new CellInfoWcdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityWcdma ci = new CellIdentityWcdma(
                LAC, CID, PSC, UARFCN, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthWcdma cs =
                new CellSignalStrengthWcdma(RSSI, BIT_ERROR_RATE, RSCP, ECNO);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoWcdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoWcdma);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForWcdmaWithEmptyOperatorInfo() {
        ArrayList<CellInfo> ret = getCellInfoListForWcdma(
                MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) ret.get(0);
        CellInfoWcdma expected = new CellInfoWcdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityWcdma ci = new CellIdentityWcdma(
                LAC, CID, PSC, UARFCN, MCC_STR, MNC_STR, EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthWcdma cs = new CellSignalStrengthWcdma(
                RSSI, BIT_ERROR_RATE, RSCP, ECNO);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoWcdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoWcdma);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForWcdmaWithEmptyMccMnc() {
        // MCC/MNC will be set as INT_MAX if unknown
        ArrayList<CellInfo> ret = getCellInfoListForWcdma(null, null, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) ret.get(0);
        CellInfoWcdma expected = new CellInfoWcdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityWcdma ci = new CellIdentityWcdma(
                LAC, CID, PSC, UARFCN, null, null, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        CellSignalStrengthWcdma cs = new CellSignalStrengthWcdma(
                RSSI, BIT_ERROR_RATE, RSCP, ECNO);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoWcdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoWcdma);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForCdma() {
        ArrayList<CellInfo> ret = getCellInfoListForCdma(ALPHA_LONG, ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoCdma cellInfoCdma = (CellInfoCdma) ret.get(0);
        CellInfoCdma expected = new CellInfoCdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityCdma ci = new CellIdentityCdma(
                NETWORK_ID, SYSTEM_ID, BASESTATION_ID, LONGITUDE, LATITUDE,
                ALPHA_LONG, ALPHA_SHORT);
        CellSignalStrengthCdma cs = new CellSignalStrengthCdma(
                DBM, ECIO, DBM, ECIO, SIGNAL_NOISE_RATIO);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoCdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoCdma);
    }

    @Test
    public void testConvertHalCellInfoList_1_2ForCdmaWithEmptyOperatorInfo() {
        ArrayList<CellInfo> ret = getCellInfoListForCdma(EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT);

        assertEquals(1, ret.size());
        CellInfoCdma cellInfoCdma = (CellInfoCdma) ret.get(0);
        CellInfoCdma expected = new CellInfoCdma();
        expected.setRegistered(false);
        expected.setTimeStamp(TIMESTAMP);
        CellIdentityCdma ci = new CellIdentityCdma(
                NETWORK_ID, SYSTEM_ID, BASESTATION_ID, LONGITUDE, LATITUDE,
                EMPTY_ALPHA_LONG, EMPTY_ALPHA_SHORT);
        CellSignalStrengthCdma cs = new CellSignalStrengthCdma(
                DBM, ECIO, DBM, ECIO, SIGNAL_NOISE_RATIO);
        expected.setCellIdentity(ci);
        expected.setCellSignalStrength(cs);
        expected.setCellConnectionStatus(CellInfo.CONNECTION_NONE);
        cellInfoCdma.setTimeStamp(TIMESTAMP); // override the timestamp
        assertEquals(expected, cellInfoCdma);
    }

    @Test
    public void testConvertHalCellInfoList_1_4ForNr() {
        android.hardware.radio.V1_4.CellInfoNr cellinfo =
                new android.hardware.radio.V1_4.CellInfoNr();
        cellinfo.cellidentity.nci = CI;
        cellinfo.cellidentity.pci = PCI;
        cellinfo.cellidentity.tac = TAC;
        cellinfo.cellidentity.nrarfcn = NRARFCN;
        cellinfo.cellidentity.mcc = MCC_STR;
        cellinfo.cellidentity.mnc = MNC_STR;
        cellinfo.cellidentity.operatorNames.alphaLong = ALPHA_LONG;
        cellinfo.cellidentity.operatorNames.alphaShort = ALPHA_SHORT;
        cellinfo.signalStrength.ssRsrp = RSRP;
        cellinfo.signalStrength.ssRsrq = RSRQ;
        cellinfo.signalStrength.ssSinr = SIGNAL_NOISE_RATIO;
        cellinfo.signalStrength.csiRsrp = RSRP;
        cellinfo.signalStrength.csiRsrq = RSRQ;
        cellinfo.signalStrength.csiSinr = SIGNAL_NOISE_RATIO;

        android.hardware.radio.V1_4.CellInfo record = new android.hardware.radio.V1_4.CellInfo();
        record.info.nr(cellinfo);

        ArrayList<android.hardware.radio.V1_4.CellInfo> records = new ArrayList<>();
        records.add(record);

        ArrayList<CellInfo> ret = RIL.convertHalCellInfoList_1_4(records);

        CellInfoNr cellInfoNr = (CellInfoNr) ret.get(0);
        CellIdentityNr cellIdentityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
        CellSignalStrengthNr signalStrengthNr =
                (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();

        CellIdentityNr expectedCellIdentity = new CellIdentityNr(PCI, TAC, NRARFCN,
                new int[] {}, MCC_STR, MNC_STR, CI, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        CellSignalStrengthNr expectedSignalStrength = new CellSignalStrengthNr(-RSRP, -RSRQ,
                SIGNAL_NOISE_RATIO, -RSRP, -RSRQ, SIGNAL_NOISE_RATIO);

        assertEquals(expectedCellIdentity, cellIdentityNr);
        assertEquals(expectedSignalStrength, signalStrengthNr);
    }

    private static android.hardware.radio.V1_5.ClosedSubscriberGroupInfo getHalCsgInfo() {
        android.hardware.radio.V1_5.ClosedSubscriberGroupInfo csgInfo =
                new android.hardware.radio.V1_5.ClosedSubscriberGroupInfo();

        csgInfo.csgIndication = CSG_INDICATION;
        csgInfo.homeNodebName = HOME_NODEB_NAME;
        csgInfo.csgIdentity = CSG_IDENTITY;

        return csgInfo;
    }

    private static void initializeCellIdentityLte_1_5(
            android.hardware.radio.V1_5.CellIdentityLte id,
            boolean addAdditionalPlmns, boolean addCsgInfo) {

        initializeCellIdentityLte_1_2(id.base);

        if (addAdditionalPlmns) {
            id.additionalPlmns = new ArrayList<>(
                    Arrays.asList(ADDITIONAL_PLMNS));
        }

        if (addCsgInfo) {
            id.optionalCsgInfo.csgInfo(getHalCsgInfo());
        }
    }

    @Test
    public void testCellIdentityLte_1_5_CsgInfo() {
        android.hardware.radio.V1_5.CellIdentityLte halCellIdentity =
                new android.hardware.radio.V1_5.CellIdentityLte();
        initializeCellIdentityLte_1_5(halCellIdentity, false, true);

        CellIdentityLte cellIdentity = new CellIdentityLte(halCellIdentity);

        assertEquals(CSG_INDICATION,
                cellIdentity.getClosedSubscriberGroupInfo().getCsgIndicator());
        assertEquals(HOME_NODEB_NAME,
                cellIdentity.getClosedSubscriberGroupInfo().getHomeNodebName());
        assertEquals(CSG_IDENTITY,
                cellIdentity.getClosedSubscriberGroupInfo().getCsgIdentity());
    }

    @Test
    public void testCellIdentityLte_1_5_MultiPlmn() {
        android.hardware.radio.V1_5.CellIdentityLte halCellIdentity =
                new android.hardware.radio.V1_5.CellIdentityLte();
        initializeCellIdentityLte_1_5(halCellIdentity, true, false);

        CellIdentityLte cellIdentity = new CellIdentityLte(halCellIdentity);

        Set<String> additionalPlmns = new HashSet<>();
        Collections.addAll(additionalPlmns, ADDITIONAL_PLMNS);

        assertEquals(cellIdentity.getAdditionalPlmns(), additionalPlmns);
    }

    private static void initializeCellIdentityWcdma_1_5(
            android.hardware.radio.V1_5.CellIdentityWcdma id,
            boolean addAdditionalPlmns, boolean addCsgInfo) {

        initializeCellIdentityWcdma_1_2(id.base);

        if (addAdditionalPlmns) {
            id.additionalPlmns = new ArrayList<>(
                    Arrays.asList(ADDITIONAL_PLMNS));
        }

        if (addCsgInfo) {
            id.optionalCsgInfo.csgInfo(getHalCsgInfo());
        }
    }

    @Test
    public void testCellIdentityWcdma_1_5_CsgInfo() {
        android.hardware.radio.V1_5.CellIdentityWcdma halCellIdentity =
                new android.hardware.radio.V1_5.CellIdentityWcdma();
        initializeCellIdentityWcdma_1_5(halCellIdentity, false, true);

        CellIdentityWcdma cellIdentity = new CellIdentityWcdma(halCellIdentity);

        assertEquals(CSG_INDICATION,
                cellIdentity.getClosedSubscriberGroupInfo().getCsgIndicator());
        assertEquals(HOME_NODEB_NAME,
                cellIdentity.getClosedSubscriberGroupInfo().getHomeNodebName());
        assertEquals(CSG_IDENTITY,
                cellIdentity.getClosedSubscriberGroupInfo().getCsgIdentity());
    }

    @Test
    public void testCellIdentityWcdma_1_5_MultiPlmn() {
        android.hardware.radio.V1_5.CellIdentityWcdma halCellIdentity =
                new android.hardware.radio.V1_5.CellIdentityWcdma();
        initializeCellIdentityWcdma_1_5(halCellIdentity, true, false);

        CellIdentityWcdma cellIdentity = new CellIdentityWcdma(halCellIdentity);

        Set<String> additionalPlmns = new HashSet<>();
        Collections.addAll(additionalPlmns, ADDITIONAL_PLMNS);

        assertEquals(cellIdentity.getAdditionalPlmns(), additionalPlmns);
    }

    private static void initializeCellIdentityTdscdma_1_5(
            android.hardware.radio.V1_5.CellIdentityTdscdma id,
            boolean addAdditionalPlmns, boolean addCsgInfo) {

        initializeCellIdentityTdscdma_1_2(id.base);

        if (addAdditionalPlmns) {
            id.additionalPlmns = new ArrayList<>(
                    Arrays.asList(ADDITIONAL_PLMNS));
        }

        if (addCsgInfo) {
            id.optionalCsgInfo.csgInfo(getHalCsgInfo());
        }
    }

    @Test
    public void testCellIdentityTdscdma_1_5_CsgInfo() {
        android.hardware.radio.V1_5.CellIdentityTdscdma halCellIdentity =
                new android.hardware.radio.V1_5.CellIdentityTdscdma();
        initializeCellIdentityTdscdma_1_5(halCellIdentity, false, true);

        CellIdentityTdscdma cellIdentity = new CellIdentityTdscdma(halCellIdentity);

        assertEquals(CSG_INDICATION,
                cellIdentity.getClosedSubscriberGroupInfo().getCsgIndicator());
        assertEquals(HOME_NODEB_NAME,
                cellIdentity.getClosedSubscriberGroupInfo().getHomeNodebName());
        assertEquals(CSG_IDENTITY,
                cellIdentity.getClosedSubscriberGroupInfo().getCsgIdentity());
    }

    @Test
    public void testCellIdentityTdscdma_1_5_MultiPlmn() {
        android.hardware.radio.V1_5.CellIdentityTdscdma halCellIdentity =
                new android.hardware.radio.V1_5.CellIdentityTdscdma();
        initializeCellIdentityTdscdma_1_5(halCellIdentity, true, false);

        CellIdentityTdscdma cellIdentity = new CellIdentityTdscdma(halCellIdentity);

        Set<String> additionalPlmns = new HashSet<>();
        Collections.addAll(additionalPlmns, ADDITIONAL_PLMNS);

        assertEquals(cellIdentity.getAdditionalPlmns(), additionalPlmns);
    }

    @Test
    public void testConvertDataCallResult() {
        // Test V1.0 SetupDataCallResult
        android.hardware.radio.V1_0.SetupDataCallResult result10 =
                new android.hardware.radio.V1_0.SetupDataCallResult();
        result10.status = android.hardware.radio.V1_0.DataCallFailCause.NONE;
        result10.suggestedRetryTime = -1;
        result10.cid = 0;
        result10.active = 2;
        result10.type = "IPV4V6";
        result10.ifname = "ifname";
        result10.addresses = "10.0.2.15 2607:fb90:a620:651d:eabe:f8da:c107:44be/64";
        result10.dnses = "10.0.2.3 fd00:976a::9";
        result10.gateways = "10.0.2.15 fe80::2";
        result10.pcscf = "fd00:976a:c206:20::6   fd00:976a:c206:20::9    fd00:976a:c202:1d::9";
        result10.mtu = 1500;

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(0)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress("10.0.2.15"), 32),
                        new LinkAddress("2607:fb90:a620:651d:eabe:f8da:c107:44be/64")))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c206:20::6"),
                        InetAddresses.parseNumericAddress("fd00:976a:c206:20::9"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::9")))
                .setMtu(1500)
                .setMtuV4(1500)
                .setMtuV6(1500)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(new ArrayList<>())
                .build();

        assertEquals(response, RIL.convertDataCallResult(result10));

        // Test V1.4 SetupDataCallResult
        android.hardware.radio.V1_4.SetupDataCallResult result14 =
                new android.hardware.radio.V1_4.SetupDataCallResult();
        result14.cause = android.hardware.radio.V1_4.DataCallFailCause.NONE;
        result14.suggestedRetryTime = -1;
        result14.cid = 0;
        result14.active = android.hardware.radio.V1_4.DataConnActiveStatus.ACTIVE;
        result14.type = android.hardware.radio.V1_4.PdpProtocolType.IPV4V6;
        result14.ifname = "ifname";
        result14.addresses = new ArrayList<>(
                Arrays.asList("10.0.2.15", "2607:fb90:a620:651d:eabe:f8da:c107:44be/64"));
        result14.dnses = new ArrayList<>(Arrays.asList("10.0.2.3", "fd00:976a::9"));
        result14.gateways = new ArrayList<>(Arrays.asList("10.0.2.15", "fe80::2"));
        result14.pcscf = new ArrayList<>(Arrays.asList(
                "fd00:976a:c206:20::6", "fd00:976a:c206:20::9", "fd00:976a:c202:1d::9"));
        result14.mtu = 1500;

        assertEquals(response, RIL.convertDataCallResult(result14));

        // Test V1.5 SetupDataCallResult
        android.hardware.radio.V1_5.SetupDataCallResult result15 =
                new android.hardware.radio.V1_5.SetupDataCallResult();
        result15.cause = android.hardware.radio.V1_4.DataCallFailCause.NONE;
        result15.suggestedRetryTime = -1;
        result15.cid = 0;
        result15.active = android.hardware.radio.V1_4.DataConnActiveStatus.ACTIVE;
        result15.type = android.hardware.radio.V1_4.PdpProtocolType.IPV4V6;
        result15.ifname = "ifname";

        android.hardware.radio.V1_5.LinkAddress la1 = new android.hardware.radio.V1_5.LinkAddress();
        la1.address = "10.0.2.15";
        la1.properties = 0;
        la1.deprecationTime = -1;
        la1.expirationTime = -1;

        android.hardware.radio.V1_5.LinkAddress la2 = new android.hardware.radio.V1_5.LinkAddress();
        la2.address = "2607:fb90:a620:651d:eabe:f8da:c107:44be/64";
        la2.properties = 0;
        la2.deprecationTime = -1;
        la2.expirationTime = -1;
        result15.addresses = new ArrayList<>(Arrays.asList(la1, la2));
        result15.dnses = new ArrayList<>(Arrays.asList("10.0.2.3", "fd00:976a::9"));
        result15.gateways = new ArrayList<>(Arrays.asList("10.0.2.15", "fe80::2"));
        result15.pcscf = new ArrayList<>(Arrays.asList(
                "fd00:976a:c206:20::6", "fd00:976a:c206:20::9", "fd00:976a:c202:1d::9"));
        result15.mtuV4 = 1500;
        result15.mtuV6 = 3000;

        response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(0)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress("10.0.2.15"), 32),
                        new LinkAddress("2607:fb90:a620:651d:eabe:f8da:c107:44be/64")))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c206:20::6"),
                        InetAddresses.parseNumericAddress("fd00:976a:c206:20::9"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::9")))
                .setMtu(3000)
                .setMtuV4(1500)
                .setMtuV6(3000)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(new ArrayList<>())
                .build();

        assertEquals(response, RIL.convertDataCallResult(result15));

        // Test V1.6 SetupDataCallResult
        android.hardware.radio.V1_6.SetupDataCallResult result16 =
                new android.hardware.radio.V1_6.SetupDataCallResult();
        result16.cause = android.hardware.radio.V1_4.DataCallFailCause.NONE;
        result16.suggestedRetryTime = -1;
        result16.cid = 0;
        result16.active = android.hardware.radio.V1_4.DataConnActiveStatus.ACTIVE;
        result16.type = android.hardware.radio.V1_4.PdpProtocolType.IPV4V6;
        result16.ifname = "ifname";

        result16.addresses = new ArrayList<>(Arrays.asList(la1, la2));
        result16.dnses = new ArrayList<>(Arrays.asList("10.0.2.3", "fd00:976a::9"));
        result16.gateways = new ArrayList<>(Arrays.asList("10.0.2.15", "fe80::2"));
        result16.pcscf = new ArrayList<>(Arrays.asList(
                "fd00:976a:c206:20::6", "fd00:976a:c206:20::9", "fd00:976a:c202:1d::9"));
        result16.mtuV4 = 1500;
        result16.mtuV6 = 3000;
        result16.handoverFailureMode = android.hardware.radio.V1_6.HandoverFailureMode.LEGACY;

        // Build android.hardware.radio.V1_6.EpsQos
        android.hardware.radio.V1_6.EpsQos halEpsQos = new android.hardware.radio.V1_6.EpsQos();
        halEpsQos.qci = 4;
        halEpsQos.downlink.maxBitrateKbps = 4;
        halEpsQos.downlink.guaranteedBitrateKbps = 7;
        halEpsQos.uplink.maxBitrateKbps = 5;
        halEpsQos.uplink.guaranteedBitrateKbps = 8;

        result16.defaultQos.eps(halEpsQos);

        // android.hardware.radio.V1_6.PortRange
        android.hardware.radio.V1_6.PortRange localPort =
                new android.hardware.radio.V1_6.PortRange();
        android.hardware.radio.V1_6.PortRange remotePort =
                new android.hardware.radio.V1_6.PortRange();
        localPort.start = 123;
        localPort.end = 123;
        remotePort.start = 223;
        remotePort.end = 223;

        // android.hardware.radio.V1_6.QosFilter
        android.hardware.radio.V1_6.QosFilter halQosFilter =
                new android.hardware.radio.V1_6.QosFilter();
        halQosFilter.localAddresses = new ArrayList<>(Arrays.asList("122.22.22.22"));
        halQosFilter.remoteAddresses = new ArrayList<>(Arrays.asList("144.44.44.44"));
        halQosFilter.localPort.range(localPort);
        halQosFilter.remotePort.range(remotePort);
        halQosFilter.protocol = android.hardware.radio.V1_6.QosProtocol.UDP;
        halQosFilter.tos.value((byte)7);
        halQosFilter.flowLabel.value(987);
        halQosFilter.spi.value(678);
        halQosFilter.direction = android.hardware.radio.V1_6.QosFilterDirection.BIDIRECTIONAL;
        halQosFilter.precedence = 45;

        // android.hardware.radio.V1_6.QosSession
        android.hardware.radio.V1_6.QosSession halQosSession =
                new android.hardware.radio.V1_6.QosSession();
        halQosSession.qosSessionId = 1234;
        halQosSession.qos.eps(halEpsQos);
        halQosSession.qosFilters = new ArrayList<>(Arrays.asList(halQosFilter));

        result16.qosSessions = new ArrayList<>(Arrays.asList(halQosSession));

        EpsQos epsQos = new EpsQos(halEpsQos);
        QosBearerFilter qosFilter = new QosBearerFilter(
                Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress("122.22.22.22"), 32)),
                Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress("144.44.44.44"), 32)),
                new QosBearerFilter.PortRange(123, 123), new QosBearerFilter.PortRange(223, 223),
                QosBearerFilter.QOS_PROTOCOL_UDP, 7, 987, 678,
                QosBearerFilter.QOS_FILTER_DIRECTION_BIDIRECTIONAL, 45);
        ArrayList<QosBearerFilter> qosFilters = new ArrayList<>();
        ArrayList<QosBearerSession> qosSessions = new ArrayList<>();
        qosFilters.add(qosFilter);
        QosBearerSession qosSession = new QosBearerSession(1234, epsQos, qosFilters);
        qosSessions.add(qosSession);

        // android.hardware.radio.V1_6.TrafficDescriptor
        android.hardware.radio.V1_6.TrafficDescriptor halTrafficDescriptor =
                new android.hardware.radio.V1_6.TrafficDescriptor();
        android.hardware.radio.V1_6.OptionalDnn halDnn =
                new android.hardware.radio.V1_6.OptionalDnn();
        halDnn.value("DNN");

        android.hardware.radio.V1_6.OptionalOsAppId halOsAppId =
                new android.hardware.radio.V1_6.OptionalOsAppId();
        android.hardware.radio.V1_6.OsAppId osAppId = new android.hardware.radio.V1_6.OsAppId();
        byte[] osAppIdArray = {1, 2, 3, 4};
        osAppId.osAppId = mRILUnderTest.primitiveArrayToArrayList(osAppIdArray);
        halOsAppId.value(osAppId);

        halTrafficDescriptor.dnn = halDnn;
        halTrafficDescriptor.osAppId = halOsAppId;
        result16.trafficDescriptors = new ArrayList<>(Arrays.asList(halTrafficDescriptor));

        List<TrafficDescriptor> trafficDescriptors = Arrays.asList(
                new TrafficDescriptor("DNN", osAppIdArray));

        response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(0)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress("10.0.2.15"), 32),
                        new LinkAddress("2607:fb90:a620:651d:eabe:f8da:c107:44be/64")))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c206:20::6"),
                        InetAddresses.parseNumericAddress("fd00:976a:c206:20::9"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::9")))
                .setMtu(3000)
                .setMtuV4(1500)
                .setMtuV6(3000)
                .setHandoverFailureMode(DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY)
                .setDefaultQos(epsQos)
                .setQosBearerSessions(qosSessions)
                .setTrafficDescriptors(trafficDescriptors)
                .build();

        assertEquals(response, RIL.convertDataCallResult(result16));
    }

    @Test
    public void testGetWorksourceClientId() {
        RILRequest request = RILRequest.obtain(0, null, null);
        assertEquals(null, request.getWorkSourceClientId());

        request = RILRequest.obtain(0, null, new WorkSource());
        assertEquals(null, request.getWorkSourceClientId());

        WorkSource ws = new WorkSource();
        ws.add(100);
        request = RILRequest.obtain(0, null, ws);
        assertEquals("100:null", request.getWorkSourceClientId());

        ws = new WorkSource();
        ws.add(100, "foo");
        request = RILRequest.obtain(0, null, ws);
        assertEquals("100:foo", request.getWorkSourceClientId());

        ws = new WorkSource();
        ws.createWorkChain().addNode(100, "foo").addNode(200, "bar");
        request = RILRequest.obtain(0, null, ws);
        assertEquals("WorkChain{(100, foo), (200, bar)}", request.getWorkSourceClientId());
    }

    @Test
    public void testCellInfoTimestamp_1_4() {
        ArrayList<android.hardware.radio.V1_4.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_4.CellInfo>();

        for (int i = 0; i < 5 /* arbitrary */; i++) {
            android.hardware.radio.V1_4.CellInfo record =
                    new android.hardware.radio.V1_4.CellInfo();
            record.info = new android.hardware.radio.V1_4.CellInfo.Info();
            record.info.lte(new android.hardware.radio.V1_4.CellInfoLte());
            initializeCellInfoLte_1_2(record.info.lte().base);
            record.info.lte().base.cellIdentityLte.base.ci += i; // make them marginally unique

            records.add(record);
        }
        List<CellInfo> cil = RIL.convertHalCellInfoList_1_4(records);

        // Check that all timestamps are set to a valid number and are equal
        final long ts = cil.get(0).getTimeStamp();
        for (CellInfo ci : cil) {
            assertTrue(ci.getTimeStamp() > 0 && ci.getTimeStamp() != Long.MAX_VALUE);
            assertEquals(ci.getTimeStamp(), ts);
        }
    }

    @Test
    public void testCellInfoTimestamp_1_2() {
        ArrayList<android.hardware.radio.V1_2.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_2.CellInfo>();

        for (int i = 0; i < 5 /* arbitrary */; i++) {
            android.hardware.radio.V1_2.CellInfo record =
                    new android.hardware.radio.V1_2.CellInfo();
            record.cellInfoType = TYPE_LTE;
            record.timeStamp = Long.MAX_VALUE;
            record.registered = false;
            record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
            record.lte.add(new android.hardware.radio.V1_2.CellInfoLte());
            initializeCellInfoLte_1_2(record.lte.get(0));
            record.lte.get(0).cellIdentityLte.base.ci += i; // make them marginally unique

            records.add(record);
        }
        List<CellInfo> cil = RIL.convertHalCellInfoList_1_2(records);

        // Check that all timestamps are set to a valid number and are equal
        final long ts = cil.get(0).getTimeStamp();
        for (CellInfo ci : cil) {
            assertTrue(ci.getTimeStamp() > 0 && ci.getTimeStamp() != Long.MAX_VALUE);
            assertEquals(ci.getTimeStamp(), ts);
        }
    }

    private static void initializeCellIdentityLte_1_2(
            android.hardware.radio.V1_2.CellIdentityLte id) {
        // 1.0 fields
        id.base.mcc = MCC_STR;
        id.base.mnc = MNC_STR;
        id.base.ci = CI;
        id.base.pci = PCI;
        id.base.tac = TAC;
        id.base.earfcn = EARFCN;

        // 1.2 fields
        id.bandwidth = BANDWIDTH;
        id.operatorNames.alphaLong = ALPHA_LONG;
        id.operatorNames.alphaShort = ALPHA_SHORT;
    }

    private static void initializeCellInfoLte_1_2(android.hardware.radio.V1_2.CellInfoLte lte) {
        initializeCellIdentityLte_1_2(lte.cellIdentityLte);

        lte.signalStrengthLte.signalStrength = RSSI_ASU;
        lte.signalStrengthLte.rsrp = -RSRP;
        lte.signalStrengthLte.rsrq = -RSRQ;
        lte.signalStrengthLte.rssnr = RSSNR;
        lte.signalStrengthLte.cqi = CQI;
        lte.signalStrengthLte.timingAdvance = TIMING_ADVANCE;
    }

    private ArrayList<CellInfo> getCellInfoListForLTE(
            String mcc, String mnc, String alphaLong, String alphaShort) {
        android.hardware.radio.V1_2.CellInfoLte lte = new android.hardware.radio.V1_2.CellInfoLte();

        initializeCellInfoLte_1_2(lte);
        // Override the defaults for test-specific purposes
        lte.cellIdentityLte.operatorNames.alphaLong = alphaLong;
        lte.cellIdentityLte.operatorNames.alphaShort = alphaShort;
        lte.cellIdentityLte.base.mcc = mcc;
        lte.cellIdentityLte.base.mnc = mnc;

        android.hardware.radio.V1_2.CellInfo record = new android.hardware.radio.V1_2.CellInfo();
        record.cellInfoType = TYPE_LTE;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.lte.add(lte);
        record.connectionStatus = 0;
        ArrayList<android.hardware.radio.V1_2.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_2.CellInfo>();
        records.add(record);
        return RIL.convertHalCellInfoList_1_2(records);
    }

    private ArrayList<CellInfo> getCellInfoListForGSM(
            String mcc, String mnc, String alphaLong, String alphaShort) {
        android.hardware.radio.V1_2.CellInfoGsm cellinfo =
                new android.hardware.radio.V1_2.CellInfoGsm();
        cellinfo.cellIdentityGsm.base.lac = LAC;
        cellinfo.cellIdentityGsm.base.cid = CID;
        cellinfo.cellIdentityGsm.base.bsic = BSIC;
        cellinfo.cellIdentityGsm.base.arfcn = ARFCN;
        cellinfo.cellIdentityGsm.base.mcc = mcc;
        cellinfo.cellIdentityGsm.base.mnc = mnc;
        cellinfo.cellIdentityGsm.operatorNames.alphaLong = alphaLong;
        cellinfo.cellIdentityGsm.operatorNames.alphaShort = alphaShort;
        cellinfo.signalStrengthGsm.signalStrength = RSSI_ASU;
        cellinfo.signalStrengthGsm.bitErrorRate = BIT_ERROR_RATE;
        cellinfo.signalStrengthGsm.timingAdvance = TIMING_ADVANCE;
        android.hardware.radio.V1_2.CellInfo record = new android.hardware.radio.V1_2.CellInfo();
        record.cellInfoType = TYPE_GSM;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.gsm.add(cellinfo);
        record.connectionStatus = 0;
        ArrayList<android.hardware.radio.V1_2.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_2.CellInfo>();
        records.add(record);

        return RIL.convertHalCellInfoList_1_2(records);
    }

    private static void initializeCellIdentityWcdma_1_2(
            android.hardware.radio.V1_2.CellIdentityWcdma cid) {
        initializeCellIdentityWcdma_1_2(cid, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);
    }

    private static void initializeCellIdentityWcdma_1_2(
            android.hardware.radio.V1_2.CellIdentityWcdma cid,
                String mcc, String mnc, String alphaLong, String alphaShort) {
        cid.base.lac = LAC;
        cid.base.cid = CID;
        cid.base.psc = PSC;
        cid.base.uarfcn = UARFCN;
        cid.base.mcc = mcc;
        cid.base.mnc = mnc;
        cid.operatorNames.alphaLong = alphaLong;
        cid.operatorNames.alphaShort = alphaShort;
    }

    private ArrayList<CellInfo> getCellInfoListForWcdma(
            String mcc, String mnc, String alphaLong, String alphaShort) {
        android.hardware.radio.V1_2.CellInfoWcdma cellinfo =
                new android.hardware.radio.V1_2.CellInfoWcdma();
        initializeCellIdentityWcdma_1_2(
                cellinfo.cellIdentityWcdma, mcc, mnc, alphaLong, alphaShort);

        cellinfo.signalStrengthWcdma.base.signalStrength = RSSI_ASU;
        cellinfo.signalStrengthWcdma.base.bitErrorRate = BIT_ERROR_RATE;
        cellinfo.signalStrengthWcdma.rscp = RSCP_ASU;
        cellinfo.signalStrengthWcdma.ecno = ECNO_ASU;
        android.hardware.radio.V1_2.CellInfo record = new android.hardware.radio.V1_2.CellInfo();
        record.cellInfoType = TYPE_WCDMA;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.wcdma.add(cellinfo);
        record.connectionStatus = 0;
        ArrayList<android.hardware.radio.V1_2.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_2.CellInfo>();
        records.add(record);

        return RIL.convertHalCellInfoList_1_2(records);
    }

    private ArrayList<CellInfo> getCellInfoListForCdma(String alphaLong, String alphaShort) {
        android.hardware.radio.V1_2.CellInfoCdma cellinfo =
                new android.hardware.radio.V1_2.CellInfoCdma();
        cellinfo.cellIdentityCdma.base.networkId = NETWORK_ID;
        cellinfo.cellIdentityCdma.base.systemId = SYSTEM_ID;
        cellinfo.cellIdentityCdma.base.baseStationId = BASESTATION_ID;
        cellinfo.cellIdentityCdma.base.longitude = LONGITUDE;
        cellinfo.cellIdentityCdma.base.latitude = LATITUDE;
        cellinfo.cellIdentityCdma.operatorNames.alphaLong = alphaLong;
        cellinfo.cellIdentityCdma.operatorNames.alphaShort = alphaShort;
        cellinfo.signalStrengthCdma.dbm = -DBM;
        cellinfo.signalStrengthCdma.ecio = -ECIO;
        cellinfo.signalStrengthEvdo.dbm = -DBM;
        cellinfo.signalStrengthEvdo.ecio = -ECIO;
        cellinfo.signalStrengthEvdo.signalNoiseRatio = SIGNAL_NOISE_RATIO;
        android.hardware.radio.V1_2.CellInfo record = new android.hardware.radio.V1_2.CellInfo();
        record.cellInfoType = TYPE_CDMA;
        record.registered = false;
        record.timeStampType = RIL_TIMESTAMP_TYPE_OEM_RIL;
        record.timeStamp = TIMESTAMP;
        record.cdma.add(cellinfo);
        record.connectionStatus = 0;
        ArrayList<android.hardware.radio.V1_2.CellInfo> records =
                new ArrayList<android.hardware.radio.V1_2.CellInfo>();
        records.add(record);

        return RIL.convertHalCellInfoList_1_2(records);
    }

    @Test
    public void testSetupDataCall() throws Exception {
        DataProfile dp = new DataProfile.Builder()
                .setProfileId(PROFILE_ID)
                .setApn(APN)
                .setProtocolType(PROTOCOL)
                .setAuthType(AUTH_TYPE)
                .setUserName(USER_NAME)
                .setPassword(PASSWORD)
                .setType(TYPE)
                .setMaxConnectionsTime(MAX_CONNS_TIME)
                .setMaxConnections(MAX_CONNS)
                .setWaitTime(WAIT_TIME)
                .enable(APN_ENABLED)
                .setSupportedApnTypesBitmask(SUPPORTED_APN_TYPES_BITMASK)
                .setRoamingProtocolType(ROAMING_PROTOCOL)
                .setBearerBitmask(BEARER_BITMASK)
                .setMtu(MTU)
                .setPersistent(PERSISTENT)
                .setPreferred(false)
                .build();

        mRILUnderTest.setupDataCall(AccessNetworkConstants.AccessNetworkType.EUTRAN, dp, false,
                false, 0, null,
                DataCallResponse.PDU_SESSION_ID_NOT_SET, null, null, true, obtainMessage());
        ArgumentCaptor<DataProfileInfo> dpiCaptor = ArgumentCaptor.forClass(DataProfileInfo.class);
        verify(mRadioProxy).setupDataCall(
                mSerialNumberCaptor.capture(), eq(AccessNetworkConstants.AccessNetworkType.EUTRAN),
                dpiCaptor.capture(), eq(true), eq(false), eq(false));
        verifyRILResponse(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_SETUP_DATA_CALL);
        DataProfileInfo dpi = dpiCaptor.getValue();
        assertEquals(PROFILE_ID, dpi.profileId);
        assertEquals(APN, dpi.apn);
        assertEquals(PROTOCOL, ApnSetting.getProtocolIntFromString(dpi.protocol));
        assertEquals(AUTH_TYPE, dpi.authType);
        assertEquals(USER_NAME, dpi.user);
        assertEquals(PASSWORD, dpi.password);
        assertEquals(TYPE, dpi.type);
        assertEquals(MAX_CONNS_TIME, dpi.maxConnsTime);
        assertEquals(MAX_CONNS, dpi.maxConns);
        assertEquals(WAIT_TIME, dpi.waitTime);
        assertEquals(APN_ENABLED, dpi.enabled);
        assertEquals(SUPPORTED_APN_TYPES_BITMASK, dpi.supportedApnTypesBitmap);
        assertEquals(ROAMING_PROTOCOL, ApnSetting.getProtocolIntFromString(dpi.protocol));
        assertEquals(
                BEARER_BITMASK,
                ServiceState.convertBearerBitmaskToNetworkTypeBitmask(dpi.bearerBitmap >> 1));
        assertEquals(MTU, dpi.mtu);
    }

    @Test
    public void testFixupSignalStrength10() {
        final int gsmWcdmaRssiDbm = -65;

        // Test the positive case where rat=UMTS and SignalStrength=GSM
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS)
                .when(mServiceState).getRilVoiceRadioTechnology();

        SignalStrength gsmSignalStrength = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(gsmWcdmaRssiDbm, 1, CellInfo.UNAVAILABLE),
                new CellSignalStrengthWcdma(), new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(), new CellSignalStrengthNr());
        SignalStrength result = mRILUnderTest.fixupSignalStrength10(gsmSignalStrength);

        assertTrue(result.getCellSignalStrengths(CellSignalStrengthGsm.class).isEmpty());
        assertFalse(result.getCellSignalStrengths(CellSignalStrengthWcdma.class).isEmpty());

        // Even though the dBm values are equal, the above checks ensure that the value has
        // been migrated to WCDMA (with no change in the top-level getDbm() result).
        assertEquals(result.getDbm(), gsmSignalStrength.getDbm());

        // Test the no-op case where rat=GSM and SignalStrength=GSM
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_GSM)
                .when(mServiceState).getRilVoiceRadioTechnology();
        result = mRILUnderTest.fixupSignalStrength10(gsmSignalStrength);
        assertEquals(result, gsmSignalStrength);

        // Check that non-GSM non-WCDMA signal strengths are also passed through.
        SignalStrength lteSignalStrength = new SignalStrength(
                new CellSignalStrengthCdma(), new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(), new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(CellInfo.UNAVAILABLE,
                        -120, -10, CellInfo.UNAVAILABLE, CellInfo.UNAVAILABLE,
                        CellInfo.UNAVAILABLE), new CellSignalStrengthNr());
        SignalStrength lteResult = mRILUnderTest.fixupSignalStrength10(lteSignalStrength);

        assertEquals(lteResult, lteSignalStrength);
    }

    @Test
    public void testCreateCarrierRestrictionList() {
        ArrayList<CarrierIdentifier> carriers = new ArrayList<>();
        carriers.add(new CarrierIdentifier("110", "120", null, null, null, null));
        carriers.add(new CarrierIdentifier("210", "220", "SPN", null, null, null));
        carriers.add(new CarrierIdentifier("310", "320", null, "012345", null, null));
        carriers.add(new CarrierIdentifier("410", "420", null, null, "GID1", null));
        carriers.add(new CarrierIdentifier("510", "520", null, null, null, "GID2"));

        Carrier c1 = new Carrier();
        c1.mcc = "110";
        c1.mnc = "120";
        c1.matchType = CarrierIdentifier.MatchType.ALL;
        Carrier c2 = new Carrier();
        c2.mcc = "210";
        c2.mnc = "220";
        c2.matchType = CarrierIdentifier.MatchType.SPN;
        c2.matchData = "SPN";
        Carrier c3 = new Carrier();
        c3.mcc = "310";
        c3.mnc = "320";
        c3.matchType = CarrierIdentifier.MatchType.IMSI_PREFIX;
        c3.matchData = "012345";
        Carrier c4 = new Carrier();
        c4.mcc = "410";
        c4.mnc = "420";
        c4.matchType = CarrierIdentifier.MatchType.GID1;
        c4.matchData = "GID1";
        Carrier c5 = new Carrier();
        c5.mcc = "510";
        c5.mnc = "520";
        c5.matchType = CarrierIdentifier.MatchType.GID2;
        c5.matchData = "GID2";

        ArrayList<Carrier> expected = new ArrayList<>();
        expected.add(c1);
        expected.add(c2);
        expected.add(c3);
        expected.add(c4);
        expected.add(c5);

        ArrayList<Carrier> result = RIL.createCarrierRestrictionList(carriers);

        assertTrue(result.equals(expected));
    }

    @Test
    public void testEnableUiccApplications() throws Exception {
        // Not supported on Radio 1.0.
        mRILUnderTest.enableUiccApplications(false, obtainMessage());
        verify(mRadioProxy, never()).enableUiccApplications(anyInt(), anyBoolean());

        // Make radio version 1.5 to support the operation.
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV15);
        } catch (Exception e) {
        }
        mRILUnderTest.enableUiccApplications(false, obtainMessage());
        verify(mRadioProxy).enableUiccApplications(mSerialNumberCaptor.capture(), anyBoolean());
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                RIL_REQUEST_ENABLE_UICC_APPLICATIONS);
    }

    @Test
    public void testAreUiccApplicationsEnabled() throws Exception {
        // Not supported on Radio 1.0.
        mRILUnderTest.areUiccApplicationsEnabled(obtainMessage());
        verify(mRadioProxy, never()).areUiccApplicationsEnabled(mSerialNumberCaptor.capture());

        // Make radio version 1.5 to support the operation.
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV15);
        } catch (Exception e) {
        }
        mRILUnderTest.areUiccApplicationsEnabled(obtainMessage());
        verify(mRadioProxy).areUiccApplicationsEnabled(mSerialNumberCaptor.capture());
        verifyRILResponse(mRILUnderTest, mSerialNumberCaptor.getValue(),
                RIL_REQUEST_GET_UICC_APPLICATIONS_ENABLEMENT);
    }

    @Test
    public void testAreUiccApplicationsEnabled_nullRadioProxy() throws Exception {
        // Not supported on Radio 1.0.
        doReturn(null).when(mRILUnderTest).getRadioProxy(any());
        Message message = obtainMessage();
        mRILUnderTest.areUiccApplicationsEnabled(message);
        processAllMessages();
        verify(mRadioProxy, never()).areUiccApplicationsEnabled(mSerialNumberCaptor.capture());
        // Sending message is handled by getRadioProxy when proxy is null.
        // areUiccApplicationsEnabled shouldn't explicitly send another callback.
        assertEquals(null, message.obj);
    }

    @Test
    public void testSetGetCompatVersion() throws Exception {
        final int testRequest = RIL_REQUEST_GET_UICC_APPLICATIONS_ENABLEMENT;

        // getCompactVersion should return null before first setting
        assertNull(mRILUnderTest.getCompatVersion(testRequest));

        // first time setting any valid HalVersion will success
        mRILUnderTest.setCompatVersion(testRequest, RIL.RADIO_HAL_VERSION_1_4);
        assertEquals(RIL.RADIO_HAL_VERSION_1_4, mRILUnderTest.getCompatVersion(testRequest));

        // try to set a lower HalVersion will success
        mRILUnderTest.setCompatVersion(testRequest, RIL.RADIO_HAL_VERSION_1_3);
        assertEquals(RIL.RADIO_HAL_VERSION_1_3, mRILUnderTest.getCompatVersion(testRequest));

        // try to set a greater HalVersion will not success
        mRILUnderTest.setCompatVersion(testRequest, RIL.RADIO_HAL_VERSION_1_5);
        assertEquals(RIL.RADIO_HAL_VERSION_1_3, mRILUnderTest.getCompatVersion(testRequest));
    }

    @FlakyTest
    @Test
    public void testGetSlicingConfig() throws Exception {
        // Use Radio HAL v1.6
        try {
            replaceInstance(RIL.class, "mRadioVersion", mRILUnderTest, mRadioVersionV16);
        } catch (Exception e) {
        }
        mRILUnderTest.getSlicingConfig(obtainMessage());
        verify(mRadioProxy).getSlicingConfig(mSerialNumberCaptor.capture());
        verifyRILResponse_1_6(
                mRILUnderTest, mSerialNumberCaptor.getValue(), RIL_REQUEST_GET_SLICING_CONFIG);
    }
}
