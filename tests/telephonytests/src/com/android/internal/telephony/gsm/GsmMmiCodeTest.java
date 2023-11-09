/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Unit tests for the {@link GsmMmiCodeTest}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class GsmMmiCodeTest extends TelephonyTest {
    private static final String TEST_DIAL_STRING_SERVICE_CODE = "*67911";
    private static final String TEST_DIAL_STRING_NO_SERVICE_CODE = "*767911";
    private static final String TEST_DIAL_STRING_NON_EMERGENCY_NUMBER = "11976";
    private GsmMmiCode mGsmMmiCode;
    private GsmCdmaPhone mGsmCdmaPhoneUT;
    @Mock private FeatureFlags mFeatureFlags;

    private final Executor mExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mFeatureFlags = Mockito.mock(FeatureFlags.class);
        doReturn(mExecutor).when(mContext).getMainExecutor();
        mGsmCdmaPhoneUT = new GsmCdmaPhone(mContext, mSimulatedCommands, mNotifier, true, 0,
                PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory, (c, p) -> mImsManager,
                mFeatureFlags);
        setCarrierSupportsCallerIdVerticalServiceCodesCarrierConfig();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testIsTemporaryModeCLIRFromCarrierConfig() {
        // Test *67911 is treated as temporary mode CLIR
        doReturn(true).when(mTelephonyManager).isEmergencyNumber(TEST_DIAL_STRING_SERVICE_CODE);
        mGsmMmiCode = GsmMmiCode.newFromDialString(TEST_DIAL_STRING_SERVICE_CODE, mGsmCdmaPhoneUT,
                null, null);
        assertTrue(mGsmMmiCode.isTemporaryModeCLIR());
    }

    @Test
    public void testIsTemporaryModeCLIRForNonServiceCode() {
        // Test if prefix isn't *67 or *82
        doReturn(true).when(mTelephonyManager).isEmergencyNumber(TEST_DIAL_STRING_NO_SERVICE_CODE);
        mGsmMmiCode = GsmMmiCode.newFromDialString(TEST_DIAL_STRING_NO_SERVICE_CODE,
                mGsmCdmaPhoneUT, null, null);
        assertTrue(mGsmMmiCode == null);
    }

    @Test
    public void testIsTemporaryModeCLIRForNonEmergencyNumber() {
        // Test if dialing string isn't an emergency number
        mGsmMmiCode = GsmMmiCode.newFromDialString(TEST_DIAL_STRING_NON_EMERGENCY_NUMBER,
                mGsmCdmaPhoneUT, null, null);
        assertTrue(mGsmMmiCode == null);
    }

    @Test
    public void testNoCrashOnEmptyMessage() {
        GsmMmiCode mmi = GsmMmiCode.newNetworkInitiatedUssd(null, true, mGsmCdmaPhoneUT, null);
        try {
            mmi.onUssdFinishedError();
        } catch (Exception e) {
            fail("Shouldn't crash!!!");
        }
    }

    // The main purpose of this test is to list all the valid use cases of getControlStrings().
    @Test
    public void testGetControlStrings() {
        // Test if controlStrings list is empty when inputs are null
        ArrayList<String> controlStrings = GsmMmiCode.getControlStrings(null,
                null);
        assertThat(controlStrings).isEmpty();

        // Test control strings of Call Forwarding Unconditional
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CFU);
        assertThat(controlStrings).containsExactly("*#21", "*#002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_REGISTRATION,
                SsData.ServiceType.SS_CFU);
        assertThat(controlStrings).containsExactly("**21", "**002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CFU);
        assertThat(controlStrings).containsExactly("#21", "#002");

        // Test control strings of Call Forwarding Busy
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CF_BUSY);
        assertThat(controlStrings).containsExactly("*#67", "*#004", "*#002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_REGISTRATION,
                SsData.ServiceType.SS_CF_BUSY);
        assertThat(controlStrings).containsExactly("**67", "**004", "**002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CF_BUSY);
        assertThat(controlStrings).containsExactly("#67", "#004", "#002");

        // Test control strings of Call Forwarding No Reply
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CF_NO_REPLY);
        assertThat(controlStrings).containsExactly("*#61", "*#004", "*#002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_REGISTRATION,
                SsData.ServiceType.SS_CF_NO_REPLY);
        assertThat(controlStrings).containsExactly("**61", "**004", "**002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CF_NO_REPLY);
        assertThat(controlStrings).containsExactly("#61", "#004", "#002");

        // Test control strings of Call Forwarding Not Reachable
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CF_NOT_REACHABLE);
        assertThat(controlStrings).containsExactly("*#62", "*#004", "*#002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_REGISTRATION,
                SsData.ServiceType.SS_CF_NOT_REACHABLE);
        assertThat(controlStrings).containsExactly("**62", "**004", "**002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CF_NOT_REACHABLE);
        assertThat(controlStrings).containsExactly("#62", "#004", "#002");

        // Test control strings of Call Forwarding All
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CF_ALL);
        assertThat(controlStrings).containsExactly("*#002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_REGISTRATION,
                SsData.ServiceType.SS_CF_ALL);
        assertThat(controlStrings).containsExactly("**002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CF_ALL);
        assertThat(controlStrings).containsExactly("#002");

        // Test control strings of Call Forwarding ALl Conditional
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CF_ALL_CONDITIONAL);
        assertThat(controlStrings).containsExactly("*#004", "*#002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_REGISTRATION,
                SsData.ServiceType.SS_CF_ALL_CONDITIONAL);
        assertThat(controlStrings).containsExactly("**004", "**002");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CF_ALL_CONDITIONAL);
        assertThat(controlStrings).containsExactly("#004", "#002");

        // Test control strings of CLIP
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CLIP);
        assertThat(controlStrings).containsExactly("*#30");

        // Test control strings of CLIR
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_CLIR);
        assertThat(controlStrings).containsExactly("*#31");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_CLIR);
        assertThat(controlStrings).containsExactly("*31");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_CLIR);
        assertThat(controlStrings).containsExactly("#31");

        // Test control strings of Call Waiting
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_WAIT);
        assertThat(controlStrings).containsExactly("*#43");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_WAIT);
        assertThat(controlStrings).containsExactly("*43");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_WAIT);
        assertThat(controlStrings).containsExactly("#43");

        // Test control strings of BAOC
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_BAOC);
        assertThat(controlStrings).containsExactly("*#33", "*#330", "*#333");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_BAOC);
        assertThat(controlStrings).containsExactly("*33", "*330", "*333");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_BAOC);
        assertThat(controlStrings).containsExactly("#33", "#330", "#333");

        // Test control strings of BAOIC
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_BAOIC);
        assertThat(controlStrings).containsExactly("*#331", "*#330", "*#333");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_BAOIC);
        assertThat(controlStrings).containsExactly("*331", "*330", "*333");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_BAOIC);
        assertThat(controlStrings).containsExactly("#331", "#330", "#333");

        // Test control strings of BAOICxH
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_BAOIC_EXC_HOME);
        assertThat(controlStrings).containsExactly("*#332", "*#330", "*#333");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_BAOIC_EXC_HOME);
        assertThat(controlStrings).containsExactly("*332", "*330", "*333");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_BAOIC_EXC_HOME);
        assertThat(controlStrings).containsExactly("#332", "#330", "#333");

        // Test control strings of BAIC
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_BAIC);
        assertThat(controlStrings).containsExactly("*#35", "*#330", "*#353");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_BAIC);
        assertThat(controlStrings).containsExactly("*35", "*330", "*353");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_BAIC);
        assertThat(controlStrings).containsExactly("#35", "#330", "#353");

        // Test control strings of BAICr
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_BAIC_ROAMING);
        assertThat(controlStrings).containsExactly("*#351", "*#330", "*#353");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_BAIC_ROAMING);
        assertThat(controlStrings).containsExactly("*351", "*330", "*353");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_BAIC_ROAMING);
        assertThat(controlStrings).containsExactly("#351", "#330", "#353");

        // Test control strings of BA_ALL
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_ALL_BARRING);
        assertThat(controlStrings).containsExactly("*#330");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_ALL_BARRING);
        assertThat(controlStrings).containsExactly("*330");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_ALL_BARRING);
        assertThat(controlStrings).containsExactly( "#330");

        // Test control strings of BA_MO
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_OUTGOING_BARRING);
        assertThat(controlStrings).containsExactly("*#333", "*#330");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_OUTGOING_BARRING);
        assertThat(controlStrings).containsExactly("*333", "*330");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_OUTGOING_BARRING);
        assertThat(controlStrings).containsExactly( "#333", "#330");

        // Test control strings of BA_MT
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_INTERROGATION,
                SsData.ServiceType.SS_INCOMING_BARRING);
        assertThat(controlStrings).containsExactly("*#353", "*#330");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_ACTIVATION,
                SsData.ServiceType.SS_INCOMING_BARRING);
        assertThat(controlStrings).containsExactly("*353", "*330");
        controlStrings = GsmMmiCode.getControlStrings(SsData.RequestType.SS_DEACTIVATION,
                SsData.ServiceType.SS_INCOMING_BARRING);
        assertThat(controlStrings).containsExactly( "#353", "#330");
    }

    @Test
    public void testGetControlStringsForPwd() {
        // Test if controlStrings list is empty when inputs are null
        ArrayList<String> controlStrings = GsmMmiCode.getControlStringsForPwd(null,
                null);
        assertThat(controlStrings).isEmpty();

        // Test control strings of Call Barring Change Password
        controlStrings = GsmMmiCode.getControlStringsForPwd(
                SsData.RequestType.SS_REGISTRATION, SsData.ServiceType.SS_ALL_BARRING);
        assertThat(controlStrings).containsExactly("**03*330");
    }

    @Test
    public void testOperationNotSupported() {
        // Contrived; this is just to get a simple instance of the class.
        mGsmMmiCode = GsmMmiCode.newNetworkInitiatedUssd(null, true, mGsmCdmaPhoneUT, null);

        assertThat(mGsmMmiCode).isNotNull();
        // Emulate request not supported from the network.
        AsyncResult ar = new AsyncResult(null, null,
                new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED));
        mGsmMmiCode.getErrorMessage(ar);
        verify(mContext.getResources()).getText(
                eq(com.android.internal.R.string.mmiErrorNotSupported));
    }

    private void setCarrierSupportsCallerIdVerticalServiceCodesCarrierConfig() {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_CALLER_ID_VERTICAL_SERVICE_CODES_BOOL, true);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());
    }
}
