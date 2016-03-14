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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.ArrayList;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import android.telephony.CellInfo;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.internal.telephony.mocks.TelephonyRegistryMock;

public class DefaultPhoneNotifierTest extends TelephonyTest {

    private DefaultPhoneNotifier mDefaultPhoneNotifierUT;
    @Mock
    TelephonyRegistryMock mTelephonyRegisteryMock;
    @Mock
    SignalStrength mSignalStrength;
    @Mock
    CellInfo mCellInfo;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mServiceManagerMockedServices.put("telephony.registry", mTelephonyRegisteryMock);
        doReturn(mTelephonyRegisteryMock).when(mTelephonyRegisteryMock)
                .queryLocalInterface(anyString());

        mDefaultPhoneNotifierUT = new DefaultPhoneNotifier();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test @SmallTest
    public void testNotifyCallForwarding() throws Exception {
        mDefaultPhoneNotifierUT.notifyCallForwardingChanged(mPhone);
        verify(mTelephonyRegisteryMock).notifyCallForwardingChangedForSubscriber(eq(0), eq(false));

        doReturn(true).when(mPhone).getCallForwardingIndicator();
        doReturn(1).when(mPhone).getSubId();
        mDefaultPhoneNotifierUT.notifyCallForwardingChanged(mPhone);
        verify(mTelephonyRegisteryMock).notifyCallForwardingChangedForSubscriber(eq(1), eq(true));
    }

    @Test @SmallTest
    public void testNotifyDataActivity() throws Exception {
        //mock data activity state
        doReturn(Phone.DataActivityState.NONE).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegisteryMock).notifyDataActivityForSubscriber(eq(0),
                eq(TelephonyManager.DATA_ACTIVITY_NONE));

        doReturn(1).when(mPhone).getSubId();
        doReturn(Phone.DataActivityState.DATAIN).when(mPhone).getDataActivityState();
        mDefaultPhoneNotifierUT.notifyDataActivity(mPhone);
        verify(mTelephonyRegisteryMock).notifyDataActivityForSubscriber(eq(1),
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
        verify(mTelephonyRegisteryMock).notifySignalStrengthForSubscriber(eq(0),
                signalStrengthArgumentCaptor.capture());
        assertEquals(99, signalStrengthArgumentCaptor.getValue().getGsmSignalStrength());

        doReturn(1).when(mPhone).getSubId();
        mDefaultPhoneNotifierUT.notifySignalStrength(mPhone);
        verify(mTelephonyRegisteryMock).notifySignalStrengthForSubscriber(eq(1),
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

        verify(mTelephonyRegisteryMock).notifyCellInfoForSubscriber(eq(0),
                cellInfoArgumentCaptor.capture());
        assertEquals(mCellInfo, cellInfoArgumentCaptor.getValue().get(0));
    }

}
