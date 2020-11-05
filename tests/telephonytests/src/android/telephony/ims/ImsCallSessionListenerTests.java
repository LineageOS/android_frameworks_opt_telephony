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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.aidl.IImsCallSessionListener;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

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

    @Test
    public void testCallSessionDtmfReceived() throws Exception {
        ImsCallSessionListener mTestListener = new ImsCallSessionListener(mMockListener);
        mTestListener.callSessionDtmfReceived('A');
        mTestListener.callSessionDtmfReceived('a');
        verify(mMockListener, times(2)).callSessionDtmfReceived(eq('A'));

        mTestListener.callSessionDtmfReceived('B');
        mTestListener.callSessionDtmfReceived('b');
        verify(mMockListener, times(2)).callSessionDtmfReceived(eq('B'));

        mTestListener.callSessionDtmfReceived('0');
        verify(mMockListener, times(1)).callSessionDtmfReceived(eq('0'));

        mTestListener.callSessionDtmfReceived('*');
        verify(mMockListener, times(1)).callSessionDtmfReceived(eq('*'));
        mTestListener.callSessionDtmfReceived('#');
        verify(mMockListener, times(1)).callSessionDtmfReceived(eq('#'));

        try {
            mTestListener.callSessionDtmfReceived('P');
            fail("expected exception");
        } catch (IllegalArgumentException illegalArgumentException) {
            // expected
        }
    }

    @Test
    public void testCallSessionRtpExtensionHeadersReceived() throws Exception {
        ImsCallSessionListener mTestListener = new ImsCallSessionListener(mMockListener);
        ArraySet<RtpHeaderExtension> headers = new ArraySet<RtpHeaderExtension>();
        RtpHeaderExtension extension = new RtpHeaderExtension(1, new byte[1]);
        headers.add(extension);
        mTestListener.callSessionRtpHeaderExtensionsReceived(headers);
        final ArgumentCaptor<List<RtpHeaderExtension>> listCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mMockListener).callSessionRtpHeaderExtensionsReceived(
                listCaptor.capture());
        assertEquals(1, listCaptor.getValue().size());
        assertEquals(extension.getLocalIdentifier(),
                listCaptor.getValue().get(0).getLocalIdentifier());
        try {
            mTestListener.callSessionRtpHeaderExtensionsReceived(null);
            fail("expected exception");
        } catch (NullPointerException npe) {
            // expected
        }
    }
}
