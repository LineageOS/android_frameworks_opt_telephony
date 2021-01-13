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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsManager;
import android.test.FlakyTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Singleton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashMap;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SmsDispatchersControllerTest extends TelephonyTest {
    @Mock
    private SMSDispatcher.SmsTracker mTracker;

    private SmsDispatchersController mSmsDispatchersController;
    private boolean mInjectionCallbackTriggered = false;
    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        setupMockPackagePermissionChecks();

        mSmsDispatchersController = new SmsDispatchersController(mPhone, mSmsStorageMonitor,
            mSmsUsageMonitor);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mSmsDispatchersController.dispose();
        mSmsDispatchersController = null;
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

    private void switchImsSmsFormat(int phoneType) {
        mSimulatedCommands.setImsRegistrationState(new int[]{1, phoneType});
        mSimulatedCommands.notifyImsNetworkStateChanged();
        /* handle EVENT_IMS_STATE_DONE */
        processAllMessages();
        assertTrue(mSmsDispatchersController.isIms());
    }
}
