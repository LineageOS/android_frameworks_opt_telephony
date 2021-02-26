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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.DisconnectCause;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDisconnectCause;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.PhoneInternalInterface.DataActivityState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultPhoneNotifierTest extends TelephonyTest {
    private static final int PHONE_ID = 1;
    private static final int SUB_ID = 0;

    private DefaultPhoneNotifier mDefaultPhoneNotifierUT;
    @Mock
    SignalStrength mSignalStrength;
    @Mock
    CellInfo mCellInfo;
    @Mock
    GsmCdmaCall mForeGroundCall;
    @Mock
    GsmCdmaCall mBackGroundCall;
    @Mock
    GsmCdmaCall mRingingCall;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDefaultPhoneNotifierUT = new DefaultPhoneNotifier(mContext);
    }

    @After
    public void tearDown() throws Exception {
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
        //mock data activity state
        doReturn(DataActivityState.NONE).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(0),
                eq(TelephonyManager.DATA_ACTIVITY_NONE));

        doReturn(1).when(mPhone).getSubId();
        doReturn(DataActivityState.DATAIN).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegistryManager).notifyDataActivityChanged(eq(1),
                eq(TelephonyManager.DATA_ACTIVITY_IN));
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

        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(0)).notifyPreciseCallState(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

        doReturn(mForeGroundCall).when(mPhone).getForegroundCall();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(0)).notifyPreciseCallState(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

        doReturn(mBackGroundCall).when(mPhone).getBackgroundCall();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(0)).notifyPreciseCallState(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

        doReturn(mRingingCall).when(mPhone).getRingingCall();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(1)).notifyPreciseCallState(
                mPhone.getPhoneId(),
                mPhone.getSubId(),
                PreciseCallState.PRECISE_CALL_STATE_IDLE,
                PreciseCallState.PRECISE_CALL_STATE_IDLE,
                PreciseCallState.PRECISE_CALL_STATE_IDLE);

        doReturn(Call.State.ACTIVE).when(mForeGroundCall).getState();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(1)).notifyPreciseCallState(
                mPhone.getPhoneId(),
                mPhone.getSubId(),
                PreciseCallState.PRECISE_CALL_STATE_IDLE,
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                PreciseCallState.PRECISE_CALL_STATE_IDLE);

        doReturn(Call.State.HOLDING).when(mBackGroundCall).getState();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(1)).notifyPreciseCallState(
                mPhone.getPhoneId(),
                mPhone.getSubId(),
                PreciseCallState.PRECISE_CALL_STATE_IDLE,
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                PreciseCallState.PRECISE_CALL_STATE_HOLDING);

        doReturn(Call.State.ALERTING).when(mRingingCall).getState();
        mDefaultPhoneNotifierUT.notifyPreciseCallState(mPhone);
        verify(mTelephonyRegistryManager, times(1)).notifyPreciseCallState(
                mPhone.getPhoneId(),
                mPhone.getSubId(),
                PreciseCallState.PRECISE_CALL_STATE_ALERTING,
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                PreciseCallState.PRECISE_CALL_STATE_HOLDING);
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
