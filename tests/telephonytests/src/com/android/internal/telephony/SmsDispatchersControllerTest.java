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

package com.android.internal.telephony;

import static com.android.internal.telephony.SmsResponse.NO_ERROR_CODE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.test.FlakyTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Singleton;

import com.android.ims.ImsManager;
import com.android.internal.telephony.domainselection.DomainSelectionConnection;
import com.android.internal.telephony.domainselection.EmergencySmsDomainSelectionConnection;
import com.android.internal.telephony.domainselection.SmsDomainSelectionConnection;
import com.android.internal.telephony.emergency.EmergencyStateTracker;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SmsDispatchersControllerTest extends TelephonyTest {
    /**
     * Inherits the SmsDispatchersController to verify the protected methods.
     */
    private static class TestSmsDispatchersController extends SmsDispatchersController {
        TestSmsDispatchersController(Phone phone, SmsStorageMonitor storageMonitor,
                SmsUsageMonitor usageMonitor, Looper looper, FeatureFlags featureFlags) {
            super(phone, storageMonitor, usageMonitor, looper, featureFlags);
        }

        public DomainSelectionConnectionHolder testGetDomainSelectionConnectionHolder(
                boolean emergency) {
            return getDomainSelectionConnectionHolder(emergency);
        }

        public void testSendData(String callingPackage, String destAddr, String scAddr,
                int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent,
                boolean isForVvm) {
            sendData(callingPackage, destAddr, scAddr,
                    destPort, data, sentIntent, deliveryIntent, isForVvm);
        }

        public void testSendMultipartText(String destAddr, String scAddr,
                ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
                ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg,
                boolean persistMessage, int priority, boolean expectMore, int validityPeriod,
                long messageId) {
            sendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, messageUri,
                    callingPkg, persistMessage, priority, expectMore, validityPeriod, messageId);
        }

        public void testNotifySmsSentToEmergencyStateTracker(String destAddr, long messageId) {
            notifySmsSentToEmergencyStateTracker(destAddr, messageId);
        }

        public void testNotifySmsSentFailedToEmergencyStateTracker(String destAddr,
                long messageId) {
            notifySmsSentFailedToEmergencyStateTracker(destAddr, messageId);
        }
    }

    /**
     * Inherits the SMSDispatcher to verify the abstract or protected methods.
     */
    protected abstract static class TestSmsDispatcher extends SMSDispatcher {
        public TestSmsDispatcher(Phone phone, SmsDispatchersController smsDispatchersController) {
            super(phone, smsDispatchersController);
        }

        @Override
        public void sendData(String callingPackage, String destAddr, String scAddr, int destPort,
                byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent,
                boolean isForVvm) {
            super.sendData(callingPackage, destAddr, scAddr, destPort,
                    data, sentIntent, deliveryIntent, isForVvm);
        }

        @Override
        public void sendSms(SmsTracker tracker) {
        }

        @Override
        public String getFormat() {
            return SmsConstants.FORMAT_3GPP;
        }
    }

    /**
     * Inherits the SMSDispatcher to verify the protected methods.
     */
    protected static class TestImsSmsDispatcher extends ImsSmsDispatcher {
        public TestImsSmsDispatcher(Phone phone, SmsDispatchersController smsDispatchersController,
                FeatureConnectorFactory factory) {
            super(phone, smsDispatchersController, factory);
        }

        @Override
        public void sendData(String callingPackage, String destAddr, String scAddr, int destPort,
                byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent,
                boolean isForVvm) {
            super.sendData(callingPackage, destAddr, scAddr, destPort,
                    data, sentIntent, deliveryIntent, isForVvm);
        }

        @Override
        public String getFormat() {
            return SmsConstants.FORMAT_3GPP;
        }
    }

    private static final String ACTION_TEST_SMS_SENT = "TEST_SMS_SENT";

    // Mocked classes
    private FeatureFlags mFeatureFlags;
    private SMSDispatcher.SmsTracker mTracker;
    private PendingIntent mSentIntent;
    private TestImsSmsDispatcher mImsSmsDispatcher;
    private TestSmsDispatcher mGsmSmsDispatcher;
    private TestSmsDispatcher mCdmaSmsDispatcher;
    private SmsDomainSelectionConnection mSmsDsc;
    private EmergencySmsDomainSelectionConnection mEmergencySmsDsc;
    private EmergencyStateTracker mEmergencyStateTracker;
    private CompletableFuture<Integer> mEmergencySmsFuture;

    private TestSmsDispatchersController mSmsDispatchersController;
    private boolean mInjectionCallbackTriggered = false;
    private CompletableFuture<Integer> mDscFuture;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTracker = mock(SMSDispatcher.SmsTracker.class);
        mFeatureFlags = mock(FeatureFlags.class);
        setupMockPackagePermissionChecks();
        mSmsDispatchersController = new TestSmsDispatchersController(mPhone, mSmsStorageMonitor,
            mSmsUsageMonitor, mTestableLooper.getLooper(), mFeatureFlags);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mImsSmsDispatcher = null;
        mGsmSmsDispatcher = null;
        mCdmaSmsDispatcher = null;
        mSmsDsc = null;
        mEmergencySmsDsc = null;
        mDscFuture = null;
        mSmsDispatchersController.dispose();
        mSmsDispatchersController = null;
        mFeatureFlags = null;
        super.tearDown();
    }

    @Test @SmallTest @FlakyTest
    public void testSmsHandleStateUpdate() throws Exception {
        assertEquals(SmsConstants.FORMAT_UNKNOWN, mSmsDispatchersController.getImsSmsFormat());
        //Mock ImsNetWorkStateChange with GSM phone type
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_GSM);
        assertEquals(SmsConstants.FORMAT_3GPP, mSmsDispatchersController.getImsSmsFormat());
        assertTrue(mSmsDispatchersController.isIms());

        //Mock ImsNetWorkStateChange with Cdma Phone type
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_CDMA);
        assertEquals(SmsConstants.FORMAT_3GPP2, mSmsDispatchersController.getImsSmsFormat());
        assertTrue(mSmsDispatchersController.isIms());
    }

    @Test @SmallTest
    public void testReportSmsMemoryStatus() throws Exception {
        int eventReportMemoryStatusDone = 3;
        SmsStorageMonitor smsStorageMonnitor = new SmsStorageMonitor(mPhone);
        Message result = smsStorageMonnitor.obtainMessage(eventReportMemoryStatusDone);
        ImsSmsDispatcher mImsSmsDispatcher = Mockito.mock(ImsSmsDispatcher.class);
        mSmsDispatchersController.setImsSmsDispatcher(mImsSmsDispatcher);
        mSmsDispatchersController.reportSmsMemoryStatus(result);
        AsyncResult ar = (AsyncResult) result.obj;
        verify(mImsSmsDispatcher).onMemoryAvailable();
        assertNull(ar.exception);
    }

    @Test @SmallTest
    public void testReportSmsMemoryStatusFailure() throws Exception {
        int eventReportMemoryStatusDone = 3;
        SmsStorageMonitor smsStorageMonnitor = new SmsStorageMonitor(mPhone);
        Message result = smsStorageMonnitor.obtainMessage(eventReportMemoryStatusDone);
        mSmsDispatchersController.setImsSmsDispatcher(null);
        mSmsDispatchersController.reportSmsMemoryStatus(result);
        AsyncResult ar = (AsyncResult) result.obj;
        assertNotNull(ar.exception);
    }

    @Test @SmallTest @FlakyTest
    public void testSendImsGmsTest() throws Exception {
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_GSM);
        mSmsDispatchersController.sendText("111"/* desAddr*/, "222" /*scAddr*/, TAG,
                null, null, null, null, false, -1, false, -1, false, 0L);
        verify(mSimulatedCommandsVerifier).sendImsGsmSms(eq("038122F2"),
                eq("0100038111F100001CD3F69C989EC3C3F431BA2C9F0FDF6EBAFCCD6697E5D4F29C0E"), eq(0), eq(0),
                any(Message.class));
    }

    @Test @SmallTest
    public void testSendImsGmsTestWithOutDesAddr() throws Exception {
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_GSM);
        mSmsDispatchersController.sendText(null, "222" /*scAddr*/, TAG,
                null, null, null, null, false, -1, false, -1, false, 0L);
        verify(mSimulatedCommandsVerifier, times(0)).sendImsGsmSms(anyString(), anyString(),
                anyInt(), anyInt(), any(Message.class));
    }

    @Test @SmallTest @FlakyTest /* flakes 0.73% of the time on gce, 0.57% on marlin */
    public void testSendImsCdmaTest() throws Exception {
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_CDMA);
        mSmsDispatchersController.sendText("111"/* desAddr*/, "222" /*scAddr*/, TAG,
                null, null, null, null, false, -1, false, -1, false, 0L);
        verify(mSimulatedCommandsVerifier).sendImsCdmaSms((byte[])any(), eq(0), eq(0),
                any(Message.class));
    }

    @Test @SmallTest @FlakyTest /* flakes 0.71% of the time on marlin, 0.61% on gce */
    public void testSendRetrySmsCdmaTest() throws Exception {
        // newFormat will be based on voice technology
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_CDMA);
        replaceInstance(SMSDispatcher.SmsTracker.class, "mFormat", mTracker,
                SmsConstants.FORMAT_3GPP2);
        doReturn(PhoneConstants.PHONE_TYPE_CDMA).when(mPhone).getPhoneType();
        mSmsDispatchersController.sendRetrySms(mTracker);
        verify(mSimulatedCommandsVerifier).sendImsCdmaSms(captor.capture(), eq(0), eq(0),
                any(Message.class));
        assertEquals(1, captor.getAllValues().size());
        assertNull(captor.getAllValues().get(0));
    }

    @Test @SmallTest @FlakyTest /* flakes 0.85% of the time on gce, 0.43% on marlin */
    public void testSendRetrySmsGsmTest() throws Exception {
        // newFormat will be based on voice technology will be GSM if phone type is not CDMA
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_GSM);
        replaceInstance(SMSDispatcher.SmsTracker.class, "mFormat", mTracker,
                SmsConstants.FORMAT_3GPP);
        mSmsDispatchersController.sendRetrySms(mTracker);
        verify(mSimulatedCommandsVerifier).sendImsGsmSms((String)isNull(), (String)isNull(), eq(0),
                eq(0), any(Message.class));
    }

    @Test @SmallTest
    public void testSendRetrySmsNullPdu() throws Exception {
        HashMap<String, Object> map = new HashMap<>();
        map.put("scAddr", "");
        map.put("destAddr", "");
        map.put("text", null);
        map.put("destPort", 0);
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_GSM);
        replaceInstance(SMSDispatcher.SmsTracker.class, "mFormat", mTracker,
                SmsConstants.FORMAT_3GPP2);
        when(mTracker.getData()).thenReturn(map);
        mSmsDispatchersController.sendRetrySms(mTracker);
        verify(mTracker).onFailed(eq(mContext), eq(SmsManager.RESULT_SMS_SEND_RETRY_FAILED),
                eq(NO_ERROR_CODE));
    }

    @Test @SmallTest
    public void testInjectNullSmsPdu() throws Exception {
        // unmock ActivityManager to be able to register receiver, create real PendingIntent and
        // receive TEST_INTENT
        restoreInstance(Singleton.class, "mInstance", mIActivityManagerSingleton);
        restoreInstance(ActivityManager.class, "IActivityManagerSingleton", null);

        // inject null sms pdu. This should cause intent to be received since pdu is null.
        mSmsDispatchersController.injectSmsPdu(null, SmsConstants.FORMAT_3GPP, true,
                (SmsDispatchersController.SmsInjectionCallback) result -> {
                    mInjectionCallbackTriggered = true;
                   assertEquals(Intents.RESULT_SMS_GENERIC_ERROR, result);
                }
        );
        processAllMessages();
        assertEquals(true, mInjectionCallbackTriggered);
    }

    @Test @SmallTest
    public void testSendImsGmsTestWithSmsc() {
        IccSmsInterfaceManager iccSmsInterfaceManager = Mockito.mock(IccSmsInterfaceManager.class);
        when(mPhone.getIccSmsInterfaceManager()).thenReturn(iccSmsInterfaceManager);
        when(iccSmsInterfaceManager.getSmscAddressFromIccEf("com.android.messaging"))
                .thenReturn("222");
        switchImsSmsFormat(PhoneConstants.PHONE_TYPE_GSM);

        mSmsDispatchersController.sendText("111", null /*scAddr*/, TAG,
                null, null, null, "com.android.messaging",
                false, -1, false, -1, false, 0L);
        byte[] smscbyte = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(
                "222");
        String smsc = IccUtils.bytesToHexString(smscbyte);
        verify(mSimulatedCommandsVerifier).sendImsGsmSms(eq(smsc), anyString(),
                anyInt(), anyInt(), any(Message.class));
    }

    @Test
    @SmallTest
    public void testSendDataWhenDomainPs() throws Exception {
        sendDataWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, false);
    }

    @Test
    @SmallTest
    public void testSendDataWhenDomainCsAndCdma() throws Exception {
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_CDMA);
        sendDataWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, true);
    }

    @Test
    @SmallTest
    public void testSendDataWhenDomainCsAndGsm() throws Exception {
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        sendDataWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, false);
    }

    @Test
    @SmallTest
    public void testSendTextWhenDomainPs() throws Exception {
        sendTextWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, false);
    }

    @Test
    @SmallTest
    public void testSendTextWhenDomainCsAndCdma() throws Exception {
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_CDMA);
        sendTextWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, true);
    }

    @Test
    @SmallTest
    public void testSendTextWhenDomainCsAndGsm() throws Exception {
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        sendTextWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, false);
    }

    @Test
    @SmallTest
    public void testSendMultipartTextWhenDomainPs() throws Exception {
        sendMultipartTextWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, false);
    }

    @Test
    @SmallTest
    public void testSendMultipartTextWhenDomainCsAndCdma() throws Exception {
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_CDMA);
        sendMultipartTextWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, true);
    }

    @Test
    @SmallTest
    public void testSendMultipartTextWhenDomainCsAndGsm() throws Exception {
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        sendMultipartTextWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS, false);
    }

    @Test
    @SmallTest
    public void testSendRetrySmsWhenDomainPs() throws Exception {
        sendRetrySmsWithDomainSelection(NetworkRegistrationInfo.DOMAIN_PS,
                PhoneConstants.PHONE_TYPE_GSM, SmsConstants.FORMAT_3GPP);
    }

    @Test
    @SmallTest
    public void testSendRetrySmsWhenDomainCsAndCdma() throws Exception {
        sendRetrySmsWithDomainSelection(NetworkRegistrationInfo.DOMAIN_CS,
                PhoneConstants.PHONE_TYPE_CDMA, SmsConstants.FORMAT_3GPP2);
    }

    @Test
    @SmallTest
    public void testSendRetrySmsWhenDomainCsAndGsm() throws Exception {
        sendRetrySmsWithDomainSelection(NetworkRegistrationInfo.DOMAIN_CS,
                PhoneConstants.PHONE_TYPE_GSM, SmsConstants.FORMAT_3GPP);
    }

    @Test
    @SmallTest
    public void testSendRetrySmsWhenImsAlreadyUsedAndCdma() throws Exception {
        sendRetrySmsWhenImsAlreadyUsed(PhoneConstants.PHONE_TYPE_CDMA, SmsConstants.FORMAT_3GPP2);
    }

    @Test
    @SmallTest
    public void testSendRetrySmsWhenImsAlreadyUsedAndGsm() throws Exception {
        sendRetrySmsWhenImsAlreadyUsed(PhoneConstants.PHONE_TYPE_GSM, SmsConstants.FORMAT_3GPP);
    }

    @Test
    @SmallTest
    public void testSendEmergencyTextWhenDomainPs() throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        setUpEmergencyStateTracker(DisconnectCause.NOT_DISCONNECTED);

        mSmsDispatchersController.sendText("911", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(true);
        verify(mEmergencySmsDsc).requestDomainSelection(any(), any());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isEmergency());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        mDscFuture.complete(NetworkRegistrationInfo.DOMAIN_PS);
        processAllMessages();

        verify(mEmergencySmsDsc).finishSelection();
        verify(mImsSmsDispatcher).sendText(eq("911"), eq("2222"), eq("text"), eq(mSentIntent),
                any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10), eq(false),
                eq(1L), eq(false));
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());
    }

    @Test
    @SmallTest
    public void testSendEmergencyTextWhenEmergencyStateTrackerReturnsFailure() throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        setUpEmergencyStateTracker(DisconnectCause.OUT_OF_SERVICE);

        mSmsDispatchersController.sendText("911", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        // Verify the domain selection requested regardless of the result of EmergencyStateTracker.
        verify(mEmergencySmsDsc).requestDomainSelection(any(), any());
    }

    @Test
    @SmallTest
    public void testNotifySmsSentToEmergencyStateTracker() throws Exception {
        setUpDomainSelectionEnabled(true);
        setUpEmergencyStateTracker(DisconnectCause.NOT_DISCONNECTED);

        mSmsDispatchersController.testNotifySmsSentToEmergencyStateTracker("911", 1L);
        processAllMessages();

        verify(mTelephonyManager).isEmergencyNumber(eq("911"));
        verify(mEmergencyStateTracker).endSms(eq("1"), eq(true));
    }

    @Test
    @SmallTest
    public void testNotifySmsSentToEmergencyStateTrackerWithNonEmergencyNumber() throws Exception {
        setUpDomainSelectionEnabled(true);
        setUpEmergencyStateTracker(DisconnectCause.NOT_DISCONNECTED);

        mSmsDispatchersController.testNotifySmsSentToEmergencyStateTracker("1234", 1L);
        processAllMessages();

        verify(mTelephonyManager).isEmergencyNumber(eq("1234"));
        verify(mEmergencyStateTracker, never()).endSms(anyString(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testNotifySmsSentToEmergencyStateTrackerWithoutEmergencyStateTracker()
            throws Exception {
        setUpDomainSelectionEnabled(true);
        mSmsDispatchersController.testNotifySmsSentToEmergencyStateTracker("911", 1L);

        verify(mTelephonyManager, never()).isEmergencyNumber(anyString());
    }

    @Test
    @SmallTest
    public void testNotifySmsSentFailedToEmergencyStateTracker() throws Exception {
        setUpDomainSelectionEnabled(true);
        setUpEmergencyStateTracker(DisconnectCause.NOT_DISCONNECTED);

        mSmsDispatchersController.testNotifySmsSentFailedToEmergencyStateTracker("911", 1L);
        processAllMessages();

        verify(mTelephonyManager).isEmergencyNumber(eq("911"));
        verify(mEmergencyStateTracker).endSms(eq("1"), eq(false));
    }

    @Test
    @SmallTest
    public void testNotifySmsSentFailedToEmergencyStateTrackerWithNonEmergencyNumber()
            throws Exception {
        setUpDomainSelectionEnabled(true);
        setUpEmergencyStateTracker(DisconnectCause.NOT_DISCONNECTED);

        mSmsDispatchersController.testNotifySmsSentFailedToEmergencyStateTracker("1234", 1L);
        processAllMessages();

        verify(mTelephonyManager).isEmergencyNumber(eq("1234"));
        verify(mEmergencyStateTracker, never()).endSms(anyString(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testNotifyDomainSelectionTerminatedWhenImsAvailableAndNormalSms() throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        when(mImsSmsDispatcher.isAvailable()).thenReturn(true);

        mSmsDispatchersController.sendText("1111", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        ArgumentCaptor<DomainSelectionConnection.DomainSelectionConnectionCallback> captor =
                ArgumentCaptor.forClass(
                        DomainSelectionConnection.DomainSelectionConnectionCallback.class);
        verify(mSmsDsc).requestDomainSelection(any(), captor.capture());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        DomainSelectionConnection.DomainSelectionConnectionCallback callback = captor.getValue();
        assertNotNull(callback);
        callback.onSelectionTerminated(DisconnectCause.LOCAL);
        processAllMessages();

        verify(mSmsDsc, never()).finishSelection();
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());

        verify(mImsSmsDispatcher).sendText(eq("1111"), eq("2222"), eq("text"), eq(mSentIntent),
                any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10), eq(false),
                eq(1L), eq(false));
    }

    @Test
    @SmallTest
    public void testNotifyDomainSelectionTerminatedWhenImsNotAvailableAndEmergencySms()
            throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        setUpEmergencyStateTracker(DisconnectCause.NOT_DISCONNECTED);
        when(mImsSmsDispatcher.isAvailable()).thenReturn(false);
        when(mImsSmsDispatcher.isEmergencySmsSupport(anyString())).thenReturn(true);

        mSmsDispatchersController.sendText("911", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(true);
        ArgumentCaptor<DomainSelectionConnection.DomainSelectionConnectionCallback> captor =
                ArgumentCaptor.forClass(
                        DomainSelectionConnection.DomainSelectionConnectionCallback.class);
        verify(mEmergencySmsDsc).requestDomainSelection(any(), captor.capture());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        DomainSelectionConnection.DomainSelectionConnectionCallback callback = captor.getValue();
        assertNotNull(callback);
        callback.onSelectionTerminated(DisconnectCause.LOCAL);
        processAllMessages();

        verify(mEmergencySmsDsc, never()).finishSelection();
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());

        verify(mImsSmsDispatcher).sendText(eq("911"), eq("2222"), eq("text"), eq(mSentIntent),
                any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10), eq(false),
                eq(1L), eq(false));
    }

    @Test
    @SmallTest
    public void testSendTextContinuously() throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();

        mSmsDispatchersController.sendText("1111", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        mSmsDispatchersController.sendText("1111", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        verify(mSmsDsc).requestDomainSelection(any(), any());
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(2, holder.getPendingRequests().size());

        mDscFuture.complete(NetworkRegistrationInfo.DOMAIN_PS);
        processAllMessages();

        verify(mSmsDsc).finishSelection();
        verify(mImsSmsDispatcher, times(2)).sendText(eq("1111"), eq("2222"), eq("text"),
                eq(mSentIntent), any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10),
                eq(false), eq(1L), eq(false));
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());
    }

    @Test
    @SmallTest
    public void testSendTextWhenFeatureFlagDisabledForSmsDomainSelection() throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        when(mFeatureFlags.smsDomainSelectionEnabled()).thenReturn(false);

        mSmsDispatchersController.sendText("1111", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        // Expect that the domain selection logic will not be executed.
        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        assertNull(holder);
    }

    private void switchImsSmsFormat(int phoneType) {
        mSimulatedCommands.setImsRegistrationState(new int[]{1, phoneType});
        mSimulatedCommands.notifyImsNetworkStateChanged();
        /* handle EVENT_IMS_STATE_DONE */
        processAllMessages();
        assertTrue(mSmsDispatchersController.isIms());
    }

    @Test
    public void testSetImsManager() {
        ImsManager imsManager = mock(ImsManager.class);
        assertTrue(mSmsDispatchersController.setImsManager(imsManager));
    }

    private void setUpDomainSelectionEnabled(boolean enabled) {
        mSmsDispatchersController.setDomainSelectionResolverProxy(
                new SmsDispatchersController.DomainSelectionResolverProxy() {
                    @Override
                    @Nullable
                    public DomainSelectionConnection getDomainSelectionConnection(Phone phone,
                            @DomainSelectionService.SelectorType int selectorType,
                            boolean isEmergency) {
                        return null;
                    }

                    @Override
                    public boolean isDomainSelectionSupported() {
                        return true;
                    }
                });
        when(mFeatureFlags.smsDomainSelectionEnabled()).thenReturn(enabled);
    }

    private void setUpDomainSelectionConnection()  {
        mEmergencySmsDsc = Mockito.mock(EmergencySmsDomainSelectionConnection.class);
        mSmsDsc = Mockito.mock(SmsDomainSelectionConnection.class);
        mSmsDispatchersController.setDomainSelectionResolverProxy(
                new SmsDispatchersController.DomainSelectionResolverProxy() {
                    @Override
                    @Nullable
                    public DomainSelectionConnection getDomainSelectionConnection(Phone phone,
                            @DomainSelectionService.SelectorType int selectorType,
                            boolean isEmergency) {
                        return isEmergency ? mEmergencySmsDsc : mSmsDsc;
                    }

                    @Override
                    public boolean isDomainSelectionSupported() {
                        return true;
                    }
                });
        when(mFeatureFlags.smsDomainSelectionEnabled()).thenReturn(true);

        mDscFuture = new CompletableFuture<>();
        when(mSmsDsc.requestDomainSelection(
                any(DomainSelectionService.SelectionAttributes.class),
                any(DomainSelectionConnection.DomainSelectionConnectionCallback.class)))
                .thenReturn(mDscFuture);
        when(mEmergencySmsDsc.requestDomainSelection(
                any(DomainSelectionService.SelectionAttributes.class),
                any(DomainSelectionConnection.DomainSelectionConnectionCallback.class)))
                .thenReturn(mDscFuture);
    }

    private void setUpSmsDispatchers() throws Exception {
        mImsSmsDispatcher = Mockito.mock(TestImsSmsDispatcher.class);
        mGsmSmsDispatcher = Mockito.mock(TestSmsDispatcher.class);
        mCdmaSmsDispatcher = Mockito.mock(TestSmsDispatcher.class);

        replaceInstance(SmsDispatchersController.class, "mImsSmsDispatcher",
                mSmsDispatchersController, mImsSmsDispatcher);
        replaceInstance(SmsDispatchersController.class, "mGsmDispatcher",
                mSmsDispatchersController, mGsmSmsDispatcher);
        replaceInstance(SmsDispatchersController.class, "mCdmaDispatcher",
                mSmsDispatchersController, mCdmaSmsDispatcher);

        when(mTelephonyManager.isEmergencyNumber(eq("911"))).thenReturn(true);

        mSentIntent = PendingIntent.getBroadcast(TestApplication.getAppContext(), 0,
                new Intent(ACTION_TEST_SMS_SENT), PendingIntent.FLAG_MUTABLE
                        | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
    }

    private void setUpEmergencyStateTracker(int result) throws Exception {
        mEmergencySmsFuture = new CompletableFuture<Integer>();
        mEmergencyStateTracker = Mockito.mock(EmergencyStateTracker.class);
        replaceInstance(SmsDispatchersController.class, "mEmergencyStateTracker",
                mSmsDispatchersController, mEmergencyStateTracker);
        when(mEmergencyStateTracker.startEmergencySms(any(Phone.class), anyString(), anyBoolean()))
                .thenReturn(mEmergencySmsFuture);
        doNothing().when(mEmergencyStateTracker).endSms(anyString(), anyBoolean());
        mEmergencySmsFuture.complete(result);
        when(mTelephonyManager.isEmergencyNumber(eq("911"))).thenReturn(true);
    }

    private void sendDataWithDomainSelection(@NetworkRegistrationInfo.Domain int domain,
            boolean isCdmaMo) throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();

        byte[] data = new byte[] { 0x01 };
        mSmsDispatchersController.testSendData(
                "test-app", "1111", "2222", 8080, data, mSentIntent, null, false);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        verify(mSmsDsc).requestDomainSelection(any(), any());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        mDscFuture.complete(domain);
        processAllMessages();

        verify(mSmsDsc).finishSelection();
        if (domain == NetworkRegistrationInfo.DOMAIN_PS) {
            verify(mImsSmsDispatcher).sendData(eq("test-app"), eq("1111"), eq("2222"), eq(8080),
                    eq(data), eq(mSentIntent), any(), eq(false));
        } else if (isCdmaMo) {
            verify(mCdmaSmsDispatcher).sendData(eq("test-app"), eq("1111"), eq("2222"), eq(8080),
                    eq(data), eq(mSentIntent), any(), eq(false));
        } else {
            verify(mGsmSmsDispatcher).sendData(eq("test-app"), eq("1111"), eq("2222"), eq(8080),
                    eq(data), eq(mSentIntent), any(), eq(false));
        }
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());
    }

    private void sendTextWithDomainSelection(@NetworkRegistrationInfo.Domain int domain,
            boolean isCdmaMo) throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();

        mSmsDispatchersController.sendText("1111", "2222", "text", mSentIntent, null, null,
                "test-app", false, 0, false, 10, false, 1L, false);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        verify(mSmsDsc).requestDomainSelection(any(), any());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        mDscFuture.complete(domain);
        processAllMessages();

        verify(mSmsDsc).finishSelection();
        if (domain == NetworkRegistrationInfo.DOMAIN_PS) {
            verify(mImsSmsDispatcher).sendText(eq("1111"), eq("2222"), eq("text"), eq(mSentIntent),
                    any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10), eq(false),
                    eq(1L), eq(false));
        } else if (isCdmaMo) {
            verify(mCdmaSmsDispatcher).sendText(eq("1111"), eq("2222"), eq("text"), eq(mSentIntent),
                    any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10), eq(false),
                    eq(1L), eq(false));
        } else {
            verify(mGsmSmsDispatcher).sendText(eq("1111"), eq("2222"), eq("text"), eq(mSentIntent),
                    any(), any(), eq("test-app"), eq(false), eq(0), eq(false), eq(10), eq(false),
                    eq(1L), eq(false));
        }
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());
    }

    private void sendMultipartTextWithDomainSelection(@NetworkRegistrationInfo.Domain int domain,
            boolean isCdmaMo) throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();

        ArrayList<String> parts = new ArrayList<>();
        ArrayList<PendingIntent> sentIntents = new ArrayList<>();
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();
        mSmsDispatchersController.testSendMultipartText("1111", "2222", parts, sentIntents,
                deliveryIntents, null, "test-app", false, 0, false, 10, 1L);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        verify(mSmsDsc).requestDomainSelection(any(), any());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        mDscFuture.complete(domain);
        processAllMessages();

        verify(mSmsDsc).finishSelection();
        if (domain == NetworkRegistrationInfo.DOMAIN_PS) {
            verify(mImsSmsDispatcher).sendMultipartText(eq("1111"), eq("2222"), eq(parts),
                    eq(sentIntents), eq(deliveryIntents), any(), eq("test-app"), eq(false), eq(0),
                    eq(false), eq(10), eq(1L));
        } else if (isCdmaMo) {
            verify(mCdmaSmsDispatcher).sendMultipartText(eq("1111"), eq("2222"), eq(parts),
                    eq(sentIntents), eq(deliveryIntents), any(), eq("test-app"), eq(false), eq(0),
                    eq(false), eq(10), eq(1L));
        } else {
            verify(mGsmSmsDispatcher).sendMultipartText(eq("1111"), eq("2222"), eq(parts),
                    eq(sentIntents), eq(deliveryIntents), any(), eq("test-app"), eq(false), eq(0),
                    eq(false), eq(10), eq(1L));
        }
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());
    }

    private void sendRetrySmsWithDomainSelection(@NetworkRegistrationInfo.Domain int domain,
            int phoneType, String smsFormat) throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        when(mPhone.getPhoneType()).thenReturn(phoneType);
        when(mImsSmsDispatcher.getFormat()).thenReturn(SmsConstants.FORMAT_3GPP);
        when(mCdmaSmsDispatcher.getFormat()).thenReturn(SmsConstants.FORMAT_3GPP2);
        when(mGsmSmsDispatcher.getFormat()).thenReturn(SmsConstants.FORMAT_3GPP);
        replaceInstance(SMSDispatcher.SmsTracker.class, "mFormat", mTracker, smsFormat);

        mSmsDispatchersController.sendRetrySms(mTracker);
        processAllMessages();

        SmsDispatchersController.DomainSelectionConnectionHolder holder =
                mSmsDispatchersController.testGetDomainSelectionConnectionHolder(false);
        verify(mSmsDsc).requestDomainSelection(any(), any());
        assertNotNull(holder);
        assertNotNull(holder.getConnection());
        assertTrue(holder.isDomainSelectionRequested());
        assertEquals(1, holder.getPendingRequests().size());

        mDscFuture.complete(domain);
        processAllMessages();

        verify(mSmsDsc).finishSelection();
        if (domain == NetworkRegistrationInfo.DOMAIN_PS) {
            verify(mImsSmsDispatcher).sendSms(eq(mTracker));
        } else if (SmsConstants.FORMAT_3GPP2.equals(smsFormat)) {
            verify(mCdmaSmsDispatcher).sendSms(eq(mTracker));
        } else {
            verify(mGsmSmsDispatcher).sendSms(eq(mTracker));
        }
        assertNull(holder.getConnection());
        assertFalse(holder.isDomainSelectionRequested());
        assertEquals(0, holder.getPendingRequests().size());
    }

    private void sendRetrySmsWhenImsAlreadyUsed(int phoneType, String smsFormat) throws Exception {
        setUpDomainSelectionConnection();
        setUpSmsDispatchers();
        when(mPhone.getPhoneType()).thenReturn(phoneType);
        when(mImsSmsDispatcher.getFormat()).thenReturn(SmsConstants.FORMAT_3GPP);
        when(mCdmaSmsDispatcher.getFormat()).thenReturn(SmsConstants.FORMAT_3GPP2);
        when(mGsmSmsDispatcher.getFormat()).thenReturn(SmsConstants.FORMAT_3GPP);
        replaceInstance(SMSDispatcher.SmsTracker.class, "mFormat", mTracker, smsFormat);
        mTracker.mUsesImsServiceForIms = true;

        mSmsDispatchersController.sendRetrySms(mTracker);
        processAllMessages();

        verify(mSmsDsc, never()).requestDomainSelection(any(), any());

        if (SmsConstants.FORMAT_3GPP2.equals(smsFormat)) {
            verify(mCdmaSmsDispatcher).sendSms(eq(mTracker));
        } else {
            verify(mGsmSmsDispatcher).sendSms(eq(mTracker));
        }
    }
}
