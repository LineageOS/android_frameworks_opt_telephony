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
 * limitations under the License
 */

package com.android.internal.telephony.imsphone;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSession;
import android.telephony.ims.ImsStreamMediaProfile;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsCall;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

public class ImsCallTest extends TelephonyTest {

    private Bundle mBundle;
    private ImsCallProfile mTestCallProfile;

    private final Executor mExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTestCallProfile = new ImsCallProfile();
        mBundle = mTestCallProfile.mCallExtras;
        doReturn(mExecutor).when(mContext).getMainExecutor();
    }

    @After
    public void tearDown() throws Exception {
        mBundle = null;
        mTestCallProfile = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testCallSessionProgressingAppliedMediaCaps() throws Exception {
        ImsCallSession mockSession = mock(ImsCallSession.class);
        ImsCall testImsCall = new ImsCall(mContext, mTestCallProfile);
        ImsCallProfile profile = new ImsCallProfile();
        when(mockSession.getCallProfile()).thenReturn(profile);
        testImsCall.attachSession(mockSession);

        ArgumentCaptor<ImsCallSession.Listener> listenerCaptor =
                ArgumentCaptor.forClass(ImsCallSession.Listener.class);
        verify(mockSession).setListener(listenerCaptor.capture(), any());
        ImsCallSession.Listener listener = listenerCaptor.getValue();
        assertNotNull(listener);

        // Set new profile with direction of none
        ImsStreamMediaProfile newProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_INACTIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INACTIVE,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);
        listener.callSessionProgressing(mockSession, newProfile);

        ImsStreamMediaProfile testProfile = testImsCall.getCallProfile().getMediaProfile();
        assertNotNull(testProfile);
        // Assert that the new direction was applied to the profile
        assertEquals(ImsStreamMediaProfile.DIRECTION_INACTIVE, testProfile.getAudioDirection());
    }

    @Test
    @SmallTest
    public void testSetWifiDeprecated() {
        ImsCall mTestImsCall = new ImsCall(mContext, mTestCallProfile);
        assertFalse(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
        // use deprecated API
        mBundle.putString(ImsCallProfile.EXTRA_CALL_RAT_TYPE,
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN + "");
        assertTrue(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
    }

    @Test
    @SmallTest
    public void testNullCallProfile() {
        ImsCall imsCall = new ImsCall(mContext, null /* imsCallProfile */);
        assertNotNull(imsCall);
        assertFalse(imsCall.wasVideoCall());
    }

    @Test
    @SmallTest
    public void testNonNulllVideoProfile() {
        ImsCallProfile profile = new ImsCallProfile();
        profile.mCallType = ImsCallProfile.CALL_TYPE_VT_TX;

        ImsCall imsCall = new ImsCall(mContext, profile);
        assertNotNull(imsCall);
        assertTrue(imsCall.wasVideoCall());
    }

    @Test
    @SmallTest
    public void testNullCallProfileAfterNonNull() {
        ImsCallProfile profile = new ImsCallProfile();
        profile.mCallType = ImsCallProfile.CALL_TYPE_VT_TX;

        ImsCall imsCall = new ImsCall(mContext, profile);
        assertNotNull(imsCall);
        assertTrue(imsCall.wasVideoCall());

        imsCall.setCallProfile(null);
        assertTrue(imsCall.wasVideoCall());
    }

    @Test
    @SmallTest
    public void testCloseImsCallRtt() throws Exception {
        ImsCallSession mockSession = mock(ImsCallSession.class);
        ImsStreamMediaProfile streamProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INACTIVE,
                // Full RTT mode
                ImsStreamMediaProfile.RTT_MODE_FULL);
        ImsCallProfile profile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VOICE, null /*extras*/, streamProfile);
        profile.mCallType = ImsCallProfile.CALL_TYPE_VOICE;
        ImsCall imsCall = new ImsCall(mContext, profile);
        imsCall.attachSession(mockSession);

        imsCall.sendRttMessage("test");
        verify(mockSession).sendRttMessage("test");

        //called by ImsPhoneCallTracker when the call is terminated
        imsCall.close();

        try {
            // Ensure RTT cases are handled gracefully.
            imsCall.sendRttMessage("test");
            imsCall.sendRttModifyRequest(true);
            imsCall.sendRttModifyResponse(true);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    @SmallTest
    public void testSetWifi() {
        ImsCall mTestImsCall = new ImsCall(mContext, mTestCallProfile);
        assertFalse(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
        mBundle.putInt(ImsCallProfile.EXTRA_CALL_NETWORK_TYPE,
                TelephonyManager.NETWORK_TYPE_IWLAN);
        assertTrue(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
    }

    @Test
    @SmallTest
    public void testSetWifiAlt() {
        ImsCall mTestImsCall = new ImsCall(mContext, mTestCallProfile);
        assertFalse(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
        mBundle.putString(ImsCallProfile.EXTRA_CALL_RAT_TYPE_ALT,
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN + "");
        assertTrue(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
    }

    @Test
    @SmallTest
    public void testSetLteNoWifiDeprecated() {
        ImsCall mTestImsCall = new ImsCall(mContext, mTestCallProfile);
        assertFalse(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
        mBundle.putString(ImsCallProfile.EXTRA_CALL_RAT_TYPE,
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE + "");
        assertFalse(mTestImsCall.isWifiCall());
        assertEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
    }

    @Test
    @SmallTest
    public void testSetLteNoWifi() {
        ImsCall mTestImsCall = new ImsCall(mContext, mTestCallProfile);
        assertFalse(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
        mBundle.putInt(ImsCallProfile.EXTRA_CALL_NETWORK_TYPE,
                TelephonyManager.NETWORK_TYPE_LTE);
        assertFalse(mTestImsCall.isWifiCall());
        assertEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
    }

    @Test
    @SmallTest
    public void testSetLteNoWifiAlt() {
        ImsCall mTestImsCall = new ImsCall(mContext, mTestCallProfile);
        assertFalse(mTestImsCall.isWifiCall());
        assertNotEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
        mBundle.putString(ImsCallProfile.EXTRA_CALL_RAT_TYPE_ALT,
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE + "");
        assertFalse(mTestImsCall.isWifiCall());
        assertEquals(mTestImsCall.getNetworkType(), TelephonyManager.NETWORK_TYPE_LTE);
    }
}
