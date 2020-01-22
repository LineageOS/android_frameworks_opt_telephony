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

package android.telephony.ims;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.aidl.IImsCallSessionListener;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ImsCallSessionListenerTests {

    @Mock
    IImsCallSessionListener mMockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testListenerMayHandoverDeprecated() throws Exception {
        ImsCallSessionListener mTestListener = new ImsCallSessionListener(mMockListener);
        mTestListener.callSessionMayHandover(ServiceState.RIL_RADIO_TECHNOLOGY_LTE,
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN);
        // verify we get the correct network type equivalent of this param.
        verify(mMockListener).callSessionMayHandover(TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_IWLAN);
    }

    @Test
    public void testListenerHandoverDeprecated() throws Exception {
        ImsReasonInfo imsReasonInfo = new ImsReasonInfo();
        ImsCallSessionListener mTestListener = new ImsCallSessionListener(mMockListener);
        mTestListener.callSessionHandover(ServiceState.RIL_RADIO_TECHNOLOGY_LTE,
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN, imsReasonInfo);
        // verify we get the correct network type equivalent of this param.
        verify(mMockListener).callSessionHandover(eq(TelephonyManager.NETWORK_TYPE_LTE),
                eq(TelephonyManager.NETWORK_TYPE_IWLAN), eq(imsReasonInfo));
    }

    @Test
    public void testListenerHandoverFailedDeprecated() throws Exception {
        ImsReasonInfo imsReasonInfo = new ImsReasonInfo(
                ImsReasonInfo.CODE_REJECT_ONGOING_HANDOVER, 0 /*extraCode*/);
        ImsCallSessionListener mTestListener = new ImsCallSessionListener(mMockListener);
        mTestListener.callSessionHandoverFailed(ServiceState.RIL_RADIO_TECHNOLOGY_LTE,
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN, imsReasonInfo);
        // verify we get the correct network type equivalent of this param.
        verify(mMockListener).callSessionHandoverFailed(eq(TelephonyManager.NETWORK_TYPE_LTE),
                eq(TelephonyManager.NETWORK_TYPE_IWLAN), eq(imsReasonInfo));
    }

}
