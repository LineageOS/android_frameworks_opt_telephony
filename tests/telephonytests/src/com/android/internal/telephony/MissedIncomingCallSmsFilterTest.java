/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for {@link MissedIncomingCallSmsFilter}
 */
public class MissedIncomingCallSmsFilterTest extends TelephonyTest {

    private static final String FAKE_CARRIER_SMS_ORIGINATOR = "+18584121234";

    private static final String FAKE_CALLER_ID = "6501234567";

    private MissedIncomingCallSmsFilter mFilterUT;

    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(MissedIncomingCallSmsFilterTest.class.getSimpleName());

        mBundle = mContextFixture.getCarrierConfigBundle();

        mFilterUT = new MissedIncomingCallSmsFilter(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testMissedIncomingCallwithCallerId() {
        mBundle.putStringArray(
                CarrierConfigManager.KEY_MISSED_INCOMING_CALL_SMS_ORIGINATOR_STRING_ARRAY,
                new String[]{FAKE_CARRIER_SMS_ORIGINATOR});
        mBundle.putStringArray(
                CarrierConfigManager.KEY_MISSED_INCOMING_CALL_SMS_PATTERN_STRING_ARRAY,
                new String[]{"^(?<month>0[1-9]|1[012])\\/(?<day>0[1-9]|1[0-9]|2[0-9]|3[0-1]) "
                        + "(?<hour>[0-1][0-9]|2[0-3]):(?<minute>[0-5][0-9])\\s*(?<callerId>[0-9]+)"
                        + "\\s*$"});

        String smsPduString = "07919107739667F9040B918185141232F400000210413141114A17B0D82B4603C170"
                + "BA580DA4B0D56031D98C56B3DD1A";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager).addNewIncomingCall(any(), captor.capture());

        Bundle bundle = captor.getValue();
        Uri uri = bundle.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);

        assertEquals(FAKE_CALLER_ID, uri.getSchemeSpecificPart());
    }
}
