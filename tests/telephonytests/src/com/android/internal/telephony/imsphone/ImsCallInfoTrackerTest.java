/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.ServiceState;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsCallInfoTrackerTest extends TelephonyTest {

    private ImsCallInfoTracker mImsCallInfoTracker;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mImsCallInfoTracker = new ImsCallInfoTracker(mImsPhone);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testDialingNormalCall() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c = getConnection(Call.State.DIALING, false);
        mImsCallInfoTracker.addImsCallStatus(c);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        ImsCallInfo info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.DIALING, info.getCallState());
        assertFalse(info.isIncoming());
        assertFalse(info.isEmergencyCall());
        assertEquals(EUTRAN, info.getCallRadioTech());
        assertFalse(info.isHeldByRemote());
    }

    @Test
    public void testDialingEmergencyCall() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c = getConnection(Call.State.DIALING, true);
        mImsCallInfoTracker.addImsCallStatus(c);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        ImsCallInfo info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.DIALING, info.getCallState());
        assertFalse(info.isIncoming());
        assertTrue(info.isEmergencyCall());
        assertEquals(EUTRAN, info.getCallRadioTech());
        assertFalse(info.isHeldByRemote());
    }

    @Test
    public void testIncomingCall() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c = getConnection(Call.State.INCOMING, false);
        mImsCallInfoTracker.addImsCallStatus(c);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        ImsCallInfo info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.INCOMING, info.getCallState());
        assertTrue(info.isIncoming());
        assertFalse(info.isEmergencyCall());
        assertEquals(EUTRAN, info.getCallRadioTech());
        assertFalse(info.isHeldByRemote());

        // Answer the call
        doReturn(Call.State.ACTIVE).when(c).getState();
        mImsCallInfoTracker.updateImsCallStatus(c);

        verify(mImsPhone, times(2)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.ACTIVE, info.getCallState());

        // Hold the call
        doReturn(Call.State.HOLDING).when(c).getState();
        mImsCallInfoTracker.updateImsCallStatus(c);

        verify(mImsPhone, times(3)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.HOLDING, info.getCallState());

        // Disconnect the call
        doReturn(Call.State.DISCONNECTING).when(c).getState();
        mImsCallInfoTracker.updateImsCallStatus(c);

        verify(mImsPhone, times(4)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.DISCONNECTING, info.getCallState());

        // Call disconnected
        doReturn(Call.State.DISCONNECTED).when(c).getState();
        mImsCallInfoTracker.updateImsCallStatus(c);

        verify(mImsPhone, times(5)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.IDLE, info.getCallState());
    }

    @Test
    public void testMultiCalls() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c1 = getConnection(Call.State.INCOMING, false);
        mImsCallInfoTracker.addImsCallStatus(c1);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        doReturn(Call.State.ACTIVE).when(c1).getState();
        mImsCallInfoTracker.updateImsCallStatus(c1);

        verify(mImsPhone, times(2)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        // 1st call
        ImsCallInfo info1 = imsCallInfos.get(0);

        assertNotNull(info1);
        assertEquals(1, info1.getIndex());
        assertEquals(Call.State.ACTIVE, info1.getCallState());

        // Add 2nd WAITING call
        ImsPhoneConnection c2 = getConnection(Call.State.WAITING, false);
        mImsCallInfoTracker.addImsCallStatus(c2);

        verify(mImsPhone, times(3)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(2, imsCallInfos.size());

        // 1st call
        info1 = imsCallInfos.get(0);

        assertNotNull(info1);
        assertEquals(1, info1.getIndex());
        assertEquals(Call.State.ACTIVE, info1.getCallState());

        // 2nd call
        ImsCallInfo info2 = imsCallInfos.get(1);

        assertNotNull(info2);
        assertEquals(2, info2.getIndex());
        assertEquals(Call.State.WAITING, info2.getCallState());
        assertTrue(info2.isIncoming());

        // Disconnect 1st call
        doReturn(Call.State.DISCONNECTED).when(c1).getState();
        mImsCallInfoTracker.updateImsCallStatus(c1);

        verify(mImsPhone, times(4)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(2, imsCallInfos.size());

        // 1st call
        info1 = imsCallInfos.get(0);

        assertNotNull(info1);
        assertEquals(1, info1.getIndex());
        assertEquals(Call.State.IDLE, info1.getCallState());

        // 2nd call
        info2 = imsCallInfos.get(1);

        assertNotNull(info2);
        assertEquals(2, info2.getIndex());
        assertEquals(Call.State.WAITING, info2.getCallState());

        // Answer WAITING call
        doReturn(Call.State.ACTIVE).when(c2).getState();
        mImsCallInfoTracker.updateImsCallStatus(c2);

        verify(mImsPhone, times(5)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        // 2nd call
        info2 = imsCallInfos.get(0);

        assertNotNull(info2);
        assertEquals(2, info2.getIndex());
        assertEquals(Call.State.ACTIVE, info2.getCallState());
    }

    @Test
    public void testHeldByRemote() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c = getConnection(Call.State.INCOMING, false);
        mImsCallInfoTracker.addImsCallStatus(c);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        doReturn(Call.State.ACTIVE).when(c).getState();
        mImsCallInfoTracker.updateImsCallStatus(c);

        verify(mImsPhone, times(2)).updateImsCallStatus(captor.capture(), any());

        // Hold received
        mImsCallInfoTracker.updateImsCallStatus(c, true, false);

        verify(mImsPhone, times(3)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertEquals(1, imsCallInfos.size());

        ImsCallInfo info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.ACTIVE, info.getCallState());
        assertTrue(info.isHeldByRemote());

        // Resume received
        mImsCallInfoTracker.updateImsCallStatus(c, false, true);

        verify(mImsPhone, times(4)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertEquals(1, imsCallInfos.size());

        info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.ACTIVE, info.getCallState());
        assertFalse(info.isHeldByRemote());
    }

    @Test
    public void testSortImsCallInfo() throws Exception {
        List<ImsCallInfo> imsCallInfos = new ArrayList<>();
        imsCallInfos.add(new ImsCallInfo(2));
        imsCallInfos.add(new ImsCallInfo(1));

        assertEquals(2, imsCallInfos.get(0).getIndex());
        assertEquals(1, imsCallInfos.get(1).getIndex());

        ImsCallInfoTracker.sort(imsCallInfos);

        assertEquals(1, imsCallInfos.get(0).getIndex());
        assertEquals(2, imsCallInfos.get(1).getIndex());
    }

    @Test
    public void testSrvccCompleted() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c = getConnection(Call.State.DIALING, false);
        mImsCallInfoTracker.addImsCallStatus(c);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        mImsCallInfoTracker.notifySrvccCompleted();

        verify(mImsPhone, times(2)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(0, imsCallInfos.size());
    }

    @Test
    public void testClearAllOrphanedConnections() throws Exception {
        ArgumentCaptor<List<ImsCallInfo>> captor = ArgumentCaptor.forClass(List.class);

        ImsPhoneConnection c = getConnection(Call.State.DIALING, false);
        mImsCallInfoTracker.addImsCallStatus(c);

        verify(mImsPhone, times(1)).updateImsCallStatus(captor.capture(), any());

        List<ImsCallInfo> imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        mImsCallInfoTracker.clearAllOrphanedConnections();

        verify(mImsPhone, times(2)).updateImsCallStatus(captor.capture(), any());

        imsCallInfos = captor.getValue();

        assertNotNull(imsCallInfos);
        assertEquals(1, imsCallInfos.size());

        ImsCallInfo info = imsCallInfos.get(0);

        assertNotNull(info);
        assertEquals(1, info.getIndex());
        assertEquals(Call.State.IDLE, info.getCallState());
    }

    private ImsPhoneConnection getConnection(Call.State state, boolean isEmergency) {
        ImsPhoneConnection c = mock(ImsPhoneConnection.class);
        doReturn(isEmergency).when(c).isEmergencyCall();
        doReturn(state).when(c).getState();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_LTE).when(c).getCallRadioTech();
        switch (state) {
            case INCOMING:
            case WAITING:
                doReturn(true).when(c).isIncoming();
                break;
            default:
                doReturn(false).when(c).isIncoming();
        }

        return c;
    }
}
