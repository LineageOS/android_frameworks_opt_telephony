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

package com.android.internal.telephony.imsphone;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Unit tests for the {@link ImsPhoneMmiCodeTest}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsPhoneMmiCodeTest extends TelephonyTest {
    private static final String TEST_DIAL_STRING_SERVICE_CODE = "*67911";
    private static final String TEST_DIAL_STRING_NO_SERVICE_CODE = "*767911";
    private static final String TEST_DIAL_STRING_NON_EMERGENCY_NUMBER = "11976";
    private ImsPhoneMmiCode mImsPhoneMmiCode;
    private ImsPhone mImsPhoneUT;
    @Mock private ServiceState mServiceState;

    private Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        doReturn(mExecutor).when(mContext).getMainExecutor();
        doReturn(mPhone).when(mPhone).getDefaultPhone();
        doReturn(mServiceState).when(mPhone).getServiceState();
        doReturn(false).when(mServiceState).getVoiceRoaming();
        doReturn(false).when(mPhone).supportsConversionOfCdmaCallerIdMmiCodesWhileRoaming();
        mImsPhoneUT = new ImsPhone(mContext, mNotifier, mPhone);
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
        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_SERVICE_CODE,
                mImsPhoneUT);
        assertTrue(mImsPhoneMmiCode.isTemporaryModeCLIR());
    }

    @Test
    public void testIsTemporaryModeCLIRForNonServiceCode() {
        // Test if prefix isn't *67 or *82
        doReturn(true).when(mTelephonyManager).isEmergencyNumber(TEST_DIAL_STRING_NO_SERVICE_CODE);
        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_NO_SERVICE_CODE,
                mImsPhoneUT);
        assertTrue(mImsPhoneMmiCode == null);
    }

    @Test
    public void testIsTemporaryModeCLIRForNonEmergencyNumber() {
        // Test if dialing string isn't an emergency number
        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_NON_EMERGENCY_NUMBER,
                mImsPhoneUT);
        assertTrue(mImsPhoneMmiCode == null);
    }

    @Test
    public void testNoCrashOnEmptyMessage() {
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newNetworkInitiatedUssd(null, true, mImsPhoneUT);
        try {
            mmi.onUssdFinishedError();
        } catch (Exception e) {
            fail("Shouldn't crash!!!");
        }
    }

    private void setCarrierSupportsCallerIdVerticalServiceCodesCarrierConfig() {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_CALLER_ID_VERTICAL_SERVICE_CODES_BOOL, true);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());
    }
}
