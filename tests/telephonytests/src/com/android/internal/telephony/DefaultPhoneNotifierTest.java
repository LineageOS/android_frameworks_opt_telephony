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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.DisconnectCause;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDisconnectCause;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.imsphone.ImsPhoneCall;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultPhoneNotifierTest extends TelephonyTest {
    private static final int PHONE_ID = 1;
    private static final int SUB_ID = 0;

    private DefaultPhoneNotifier mDefaultPhoneNotifierUT;

    private FeatureFlags mFeatureFlags;

    // Mocked classes
    SignalStrength mSignalStrength;
    CellInfo mCellInfo;
    GsmCdmaCall mForeGroundCall;
    GsmCdmaCall mBackGroundCall;
    GsmCdmaCall mRingingCall;
    ImsPhoneCall mImsForeGroundCall;
    ImsPhoneCall mImsBackGroundCall;
    ImsPhoneCall mImsRingingCall;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSignalStrength = mock(SignalStrength.class);
        mCellInfo = mock(CellInfo.class);
        mFeatureFlags = Mockito.mock(FeatureFlags.class);
        mForeGroundCall = mock(GsmCdmaCall.class);
        mBackGroundCall = mock(GsmCdmaCall.class);
        mRingingCall = mock(GsmCdmaCall.class);
        mImsForeGroundCall = mock(ImsPhoneCall.class);
        mImsBackGroundCall = mock(ImsPhoneCall.class);
        mImsRingingCall = mock(ImsPhoneCall.class);
        mDefaultPhoneNotifierUT = new DefaultPhoneNotifier(mContext, mFeatureFlags);
    }

    @After
    public void tearDown() throws Exception {
        mDefaultPhoneNotifierUT = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testNotifyCallForwarding() throws Exception {
        mDefaultPhoneNotifierUT.notifyCallForwardingChanged(mPhone);
        verify(mTelephonyRegistryManager).notifyCallForwardingChanged(eq(0), eq(false));

        doReturn(true).when(mPhone).getCallForwardingIndicator();
        doReturn(1).when(mPhone).getSubId();
        mDefaultPhoneNotifierUT.notifyCallForwardingChanged(mPhone);
        verify(mTelephonyRegistryManager).notifyCallForwardingChanged(eq(1), eq(true));
    }

    @Test @SmallTest
    public void testNotifyDataActivity() throws Exception {
        when(mFeatureFlags.notifyDataActivityChangedWithSlot()).thenReturn(false);
        //mock data activity state
        doReturn(TelephonyManager.DATA_ACTIVITY_NONE).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(0),
                eq(TelephonyManager.DATA_ACTIVITY_NONE));

        doReturn(1).when(mPhone).getSubId();
        doReturn(TelephonyManager.DATA_ACTIVITY_IN).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(1),
                eq(TelephonyManager.DATA_ACTIVITY_IN));
    }
    @Test @SmallTest
    public void testNotifyDataActivityWithSlot() throws Exception {
        when(mFeatureFlags.notifyDataActivityChangedWithSlot()).thenReturn(true);
        //mock data activity state
        doReturn(TelephonyManager.DATA_ACTIVITY_NONE).when(mPhone).getDataActivityState();
        doReturn(PHONE_ID).when(mPhone).getPhoneId();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(1), eq(0),
                eq(TelephonyManager.DATA_ACTIVITY_NONE));

        doReturn(1/*subId*/).when(mPhone).getSubId();
        doReturn(TelephonyManager.DATA_ACTIVITY_IN).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(1), eq(1),
                eq(TelephonyManager.DATA_ACTIVITY_IN));

        doReturn(SUB_ID).when(mPhone).getSubId();
        doReturn(TelephonyManager.DATA_ACTIVITY_NONE).when(mPhone).getDataActivityState();
        doReturn(2/*phoneId*/).when(mPhone).getPhoneId();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(2), eq(0),
                eq(TelephonyManager.DATA_ACTIVITY_NONE));

        doReturn(1/*subId*/).when(mPhone).getSubId();
        doReturn(TelephonyManager.DATA_ACTIVITY_INOUT).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(
                eq(2), eq(1), eq(TelephonyManager.DATA_ACTIVITY_INOUT));

    }

    @Test @SmallTest
    public void testNotifySignalStrength() throws Exception {
        //mock signal strength value
        doReturn(99).when(mSignalStrength).getGsmSignalStrength();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        ArgumentCaptor<SignalStrength> signalStrengthArgumentCaptor =
                ArgumentCaptor.forClass(SignalStrength.class);

        mDefaultPhoneNotifierUT.notifySignalStrength(mPhone);
        verify(mTelephonyRegistryManager).notifySignalStrengthChanged(eq(0), eq(0),
                signalStrengthArgumentCaptor.capture());
        assertEquals(99, signalStrengthArgumentCaptor.getValue().getGsmSignalStrength());

        doReturn(1).when(mPhone).getSubId();
        doReturn(2).when(mPhone).getPhoneId();
        mDefaultPhoneNotifierUT.notifySignalStrength(mPhone);
        verify(mTelephonyRegistryManager).notifySignalStrengthChanged(eq(2), eq(1),
                signalStrengthArgumentCaptor.capture());
        assertEquals(99, signalStrengthArgumentCaptor.getValue().getGsmSignalStrength());
    }

    @Test @SmallTest
    public void testNotifyCellInfo() throws Exception {
        //mock cellinfo
        List<CellInfo> mCellInfoList = new ArrayList<>();
        mCellInfoList.add(mCellInfo);
        ArgumentCaptor<List> cellInfoArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mDefaultPhoneNotifierUT.notifyCellInfo(mPhone, mCellInfoList);

        verify(mTelephonyRegistryManager).notifyCellInfoChanged(eq(0),
                cellInfoArgumentCaptor.capture());
        assertEquals(mCellInfo, cellInfoArgumentCaptor.getValue().get(0));
    }

    @Test @SmallTest
    public void testNotifyMessageWaiting() throws Exception {
        doReturn(1).when(mPhone).getPhoneId();
        mDefaultPhoneNotifierUT.notifyMessageWaitingChanged(mPhone);
        verify(mTelephonyRegistryManager).notifyMessageWaitingChanged(1, 0, false);

        doReturn(2).when(mPhone).getPhoneId();
        mDefaultPhoneNotifierUT.notifyMessageWaitingChanged(mPhone);
        verify(mTelephonyRegistryManager).notifyMessageWaitingChanged(2, 0, false);

        doReturn(1).when(mPhone).getSubId();
        mDefaultPhoneNotifierUT.notifyMessageWaitingChanged(mPhone);
        verify(mTelephonyRegistryManager).notifyMessageWaitingChanged(2, 1, false);

        doReturn(true).when(mPhone).getMessageWaitingIndicator();
        mDefaultPhoneNotifierUT.notifyMessageWaitingChanged(mPhone);
        verify(mTelephonyRegistryManager).notifyMessageWaitingChanged(2, 1, true);
    }

    @Test @SmallTest
    public void testNotifyDisconnectCause() throws Exception {
        doReturn(PHONE_ID).when(mPhone).getPhoneId();
        doReturn(SUB_ID).when(mPhone).getSubId();
        mDefaultPhoneNotifierUT.notifyDisconnectCause(mPhone, DisconnectCause.NOT_VALID,
                PreciseDisconnectCause.FDN_BLOCKED);
        verify(mTelephonyRegistryManager).notifyDisconnectCause(PHONE_ID, SUB_ID,
                DisconnectCause.NOT_VALID, PreciseDisconnectCause.FDN_BLOCKED);

        mDefaultPhoneNotifierUT.notifyDisconnectCause(mPhone, DisconnectCause.LOCAL,
                PreciseDisconnectCause.CHANNEL_NOT_AVAIL);
        verify(mTelephonyRegistryManager).notifyDisconnectCause(PHONE_ID, SUB_ID,
                DisconnectCause.LOCAL, PreciseDisconnectCause.CHANNEL_NOT_AVAIL);
    }

    @Test @SmallTest
    public void testNotifyPreciseCallState() throws Exception {
        //mock forground/background/ringing call and call state
        doReturn(Call.State.IDLE).when(mForeGroundCall).getState();
        doReturn(Call.State.IDLE).when(mBackGroundCall).getState();
        doReturn(Call.State.IDLE).when(mRingingCall).getState();

        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        verify(mTelephonyRegistryManager, times(0)).notifyPreciseCallState(
                anyInt(), anyInt(), any(), any(), any(), any());

        doReturn(mForeGroundCall).when(mPhone).getForegroundCall();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        verify(mTelephonyRegistryManager, times(0)).notifyPreciseCallState(
                anyInt(), anyInt(), any(), any(), any(), any());

        doReturn(mBackGroundCall).when(mPhone).getBackgroundCall();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        verify(mTelephonyRegistryManager, times(0)).notifyPreciseCallState(
                anyInt(), anyInt(), any(), any(), any(), any());

        doReturn(mRingingCall).when(mPhone).getRingingCall();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        ArgumentCaptor<int[]> captor = ArgumentCaptor.forClass(int[].class);
        int phoneId = mPhone.getPhoneId();
        int subId = mPhone.getSubId();
        verify(mTelephonyRegistryManager).notifyPreciseCallState(
                eq(phoneId), eq(subId), captor.capture(), eq(null), eq(null), eq(null));
        final int[] callStates = captor.getValue();
        assertEquals(3, callStates.length);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates[/*ringing call*/ 0]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates[/*foreground call*/ 1]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates[/*background call*/ 2]);

        doReturn(Call.State.ACTIVE).when(mForeGroundCall).getState();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        ArgumentCaptor<int[]> captor1 = ArgumentCaptor.forClass(int[].class);
        phoneId = mPhone.getPhoneId();
        subId = mPhone.getSubId();
        verify(mTelephonyRegistryManager, times(2)).notifyPreciseCallState(
                eq(phoneId), eq(subId), captor1.capture(), eq(null), eq(null), eq(null));
        final int[] callStates1 = captor1.getValue();
        assertEquals(3, callStates1.length);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates1[/*ringing call*/ 0]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                callStates1[/*foreground call*/ 1]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates1[/*background call*/ 2]);

        doReturn(Call.State.HOLDING).when(mBackGroundCall).getState();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        ArgumentCaptor<int[]> captor2 = ArgumentCaptor.forClass(int[].class);
        verify(mTelephonyRegistryManager, times(3)).notifyPreciseCallState(
                eq(phoneId), eq(subId), captor2.capture(), eq(null), eq(null), eq(null));
        final int[] callStates2 = captor2.getValue();
        assertEquals(3, callStates2.length);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates2[/*ringing call*/ 0]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                callStates2[/*foreground call*/ 1]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_HOLDING,
                callStates2[/*background call*/ 2]);

        doReturn(Call.State.ALERTING).when(mRingingCall).getState();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone, null, null, null);
        ArgumentCaptor<int[]> captor3 = ArgumentCaptor.forClass(int[].class);
        verify(mTelephonyRegistryManager, times(4)).notifyPreciseCallState(
                eq(phoneId), eq(subId), captor3.capture(), eq(null), eq(null), eq(null));
        final int[] callStates3 = captor3.getValue();
        assertEquals(3, callStates3.length);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ALERTING,
                callStates3[/*ringing call*/ 0]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                callStates3[/*foreground call*/ 1]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_HOLDING,
                callStates3[/*background call*/ 2]);
    }

    @Test
    public void testNotifyPreciseCallStateImsCallInfo() throws Exception {
        //mock forground/background/ringing call and call state
        doReturn(Call.State.ACTIVE).when(mImsForeGroundCall).getState();
        doReturn(Call.State.HOLDING).when(mImsBackGroundCall).getState();
        doReturn(Call.State.IDLE).when(mImsRingingCall).getState();

        doReturn(mImsForeGroundCall).when(mImsPhone).getForegroundCall();
        doReturn(mImsBackGroundCall).when(mImsPhone).getBackgroundCall();
        doReturn(mImsRingingCall).when(mImsPhone).getRingingCall();

        String[] imsCallIds = {null, "1", "2"};
        int[] imsCallServiceTypes = {ImsCallProfile.SERVICE_TYPE_NONE,
                ImsCallProfile.SERVICE_TYPE_NORMAL, ImsCallProfile.SERVICE_TYPE_NORMAL};
        int[] imsCallTypes = {ImsCallProfile.CALL_TYPE_NONE,
                ImsCallProfile.CALL_TYPE_VOICE, ImsCallProfile.CALL_TYPE_VT};

        mDefaultPhoneNotifierUT
                .notifyPreciseCallState(mImsPhone, imsCallIds, imsCallServiceTypes, imsCallTypes);
        ArgumentCaptor<int[]> callStateCaptor = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<String[]> callIdCaptor = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<int[]> callServiceTypeCaptor = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> callTypeCaptor = ArgumentCaptor.forClass(int[].class);
        int phoneId = mImsPhone.getPhoneId();
        int subId = mImsPhone.getSubId();
        verify(mTelephonyRegistryManager, times(1)).notifyPreciseCallState(
                eq(phoneId), eq(subId), callStateCaptor.capture(), callIdCaptor.capture(),
                callServiceTypeCaptor.capture(), callTypeCaptor.capture());
        final int[] callStates = callStateCaptor.getValue();
        final String[] callIds = callIdCaptor.getValue();
        final int[] callServiceTypes = callServiceTypeCaptor.getValue();
        final int[] callTypes = callTypeCaptor.getValue();
        assertEquals(3, callStates.length);
        assertEquals(3, callIds.length);
        assertEquals(3, callServiceTypes.length);
        assertEquals(3, callServiceTypes.length);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                callStates[/*ringing call*/ 0]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                callStates[/*foreground call*/ 1]);
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_HOLDING,
                callStates[/*background call*/ 2]);

        assertEquals("1", callIds[/*foreground call*/ 1]);
        assertEquals("2", callIds[/*background call*/ 2]);
        assertEquals(null, callIds[/*ringing call*/ 0]);
        assertEquals(ImsCallProfile.SERVICE_TYPE_NORMAL,
                callServiceTypes[/*foreground call*/ 1]);
        assertEquals(ImsCallProfile.SERVICE_TYPE_NORMAL,
                callServiceTypes[/*background call*/ 2]);
        assertEquals(ImsCallProfile.SERVICE_TYPE_NONE,
                callServiceTypes[/*ringing call*/ 0]);
        assertEquals(ImsCallProfile.CALL_TYPE_VOICE,
                callTypes[/*foreground call*/ 1]);
        assertEquals(ImsCallProfile.CALL_TYPE_VT,
                callTypes[/*background call*/ 2]);
        assertEquals(ImsCallProfile.SERVICE_TYPE_NONE,
                callServiceTypes[/*ringing call*/ 0]);
    }

    @Test @SmallTest
    public void testNotifyCellLocation() throws Exception {
        // mock gsm cell location
        CellIdentityGsm mGsmCellLocation = new CellIdentityGsm(
                2, 3, 0, 0, null, null, null, null, Collections.emptyList());
        doReturn(mGsmCellLocation).when(mPhone).getCurrentCellIdentity();
        ArgumentCaptor<CellIdentityGsm> cellLocationCapture =
                ArgumentCaptor.forClass(CellIdentityGsm.class);

        mDefaultPhoneNotifierUT.notifyCellLocation(mPhone, mGsmCellLocation);
        verify(mTelephonyRegistryManager).notifyCellLocation(eq(0),
                cellLocationCapture.capture());
        assertEquals(2, cellLocationCapture.getValue().asCellLocation().getLac());
        assertEquals(3, cellLocationCapture.getValue().asCellLocation().getCid());
        assertEquals(-1, cellLocationCapture.getValue().asCellLocation().getPsc());

        doReturn(1).when(mPhone).getSubId();
        mDefaultPhoneNotifierUT.notifyCellLocation(mPhone, mGsmCellLocation);
        verify(mTelephonyRegistryManager).notifyCellLocation(eq(1),
                cellLocationCapture.capture());
        assertEquals(2, cellLocationCapture.getValue().asCellLocation().getLac());
        assertEquals(3, cellLocationCapture.getValue().asCellLocation().getCid());
        assertEquals(-1, cellLocationCapture.getValue().asCellLocation().getPsc());
    }
}
