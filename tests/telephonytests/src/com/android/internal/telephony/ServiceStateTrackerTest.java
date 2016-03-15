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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.UserHandle;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static org.junit.Assert.*;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;

public class ServiceStateTrackerTest extends TelephonyTest {

    @Mock
    private DcTracker mDct;
    @Mock
    private ProxyController mProxyController;

    private ServiceStateTracker sst;
    private TelephonyManager mTelephonyManager;

    private class ServiceStateTrackerTestHandler extends HandlerThread {

        private ServiceStateTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            sst = new ServiceStateTracker(mPhone, mSimulatedCommands);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {

        logd("ServiceStateTrackerTest +Setup!");
        super.setUp("ServiceStateTrackerTest");

        doReturn(true).when(mDct).isDisconnected();
        mPhone.mDcTracker = mDct;
        mTelephonyManager = (TelephonyManager) mContextFixture.getTestDouble().
                getSystemService(Context.TELEPHONY_SERVICE);

        replaceInstance(ProxyController.class, "sProxyController", null, mProxyController);

        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_sameNamedOperatorConsideredRoaming,
                new String[]{"123456"});

        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_operatorConsideredNonRoaming,
                new String[]{"123456"});

        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA);

        int dds = SubscriptionManager.getDefaultDataSubscriptionId();
        doReturn(dds).when(mPhone).getSubId();

        new ServiceStateTrackerTestHandler(getClass().getSimpleName()).start();
        waitUntilReady();
        waitForMs(600);
        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        sst = null;
        super.tearDown();
    }

    @Test
    @MediumTest
    public void testSetRadioPower() {
        boolean oldState = mSimulatedCommands.getRadioState().isOn();
        sst.setRadioPower(!oldState);
        waitForMs(100);
        assertTrue(oldState != mSimulatedCommands.getRadioState().isOn());
    }

    @Test
    @MediumTest
    public void testNoRilTrafficAfterSetRadioPower() {
        sst.setRadioPower(true);
        final int getOperatorCallCount = mSimulatedCommands.getGetOperatorCallCount();
        final int getDataRegistrationStateCallCount =
                mSimulatedCommands.getGetDataRegistrationStateCallCount();
        final int getVoiceRegistrationStateCallCount =
                mSimulatedCommands.getGetVoiceRegistrationStateCallCount();
        final int getNetworkSelectionModeCallCount =
                mSimulatedCommands.getGetNetworkSelectionModeCallCount();
        sst.setRadioPower(false);

        waitForMs(500);
        sst.pollState();
        waitForMs(250);

        assertEquals(getOperatorCallCount, mSimulatedCommands.getGetOperatorCallCount());
        assertEquals(getDataRegistrationStateCallCount,
                mSimulatedCommands.getGetDataRegistrationStateCallCount());
        assertEquals(getVoiceRegistrationStateCallCount,
                mSimulatedCommands.getGetVoiceRegistrationStateCallCount());
        assertEquals(getNetworkSelectionModeCallCount,
                mSimulatedCommands.getGetNetworkSelectionModeCallCount());
    }

    @Test
    @MediumTest
    public void testSpnUpdateShowPlmnOnly() {
        doReturn(0x02).when(mSimRecords).getDisplayRule(anyString());
        doReturn(IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN).
                when(mUiccCardApplication3gpp).getState();

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_NETWORK_STATE_CHANGED, null));

        waitForMs(750);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextFixture.getTestDouble(), atLeast(2)).
                sendStickyBroadcastAsUser(intentArgumentCaptor.capture(), eq(UserHandle.ALL));

        // We only want to verify the intent SPN_STRINGS_UPDATED_ACTION.
        Intent intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION, intent.getAction());
        assertEquals(Intent.FLAG_RECEIVER_REPLACE_PENDING, intent.getFlags());

        Bundle b = intent.getExtras();

        // For boolean we need to make sure the key exists first
        assertTrue(b.containsKey(TelephonyIntents.EXTRA_SHOW_SPN));
        assertFalse(b.getBoolean(TelephonyIntents.EXTRA_SHOW_SPN));

        assertEquals(null, b.getString(TelephonyIntents.EXTRA_SPN));
        assertEquals(null, b.getString(TelephonyIntents.EXTRA_DATA_SPN));

        // For boolean we need to make sure the key exists first
        assertTrue(b.containsKey(TelephonyIntents.EXTRA_SHOW_PLMN));
        assertTrue(b.getBoolean(TelephonyIntents.EXTRA_SHOW_PLMN));

        assertEquals(SimulatedCommands.FAKE_LONG_NAME, b.getString(TelephonyIntents.EXTRA_PLMN));

        ArgumentCaptor<Integer> intArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mTelephonyManager).setDataNetworkTypeForPhone(anyInt(), intArgumentCaptor.capture());
        assertEquals(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA,
                intArgumentCaptor.getValue().intValue());
    }

    @Test
    @MediumTest
    public void testCellInfoList() {
        Parcel p = Parcel.obtain();
        p.writeInt(1);
        p.writeInt(1);
        p.writeInt(2);
        p.writeLong(1453510289108L);
        p.writeInt(310);
        p.writeInt(260);
        p.writeInt(123);
        p.writeInt(456);
        p.writeInt(99);
        p.writeInt(3);
        p.setDataPosition(0);

        CellInfoGsm cellInfo = CellInfoGsm.CREATOR.createFromParcel(p);

        ArrayList<CellInfo> list = new ArrayList();
        list.add(cellInfo);
        mSimulatedCommands.setCellInfoList(list);

        assertEquals(sst.getAllCellInfo(), list);
    }

    @Test
    @MediumTest
    public void testImsRegState() {
        // Simulate IMS registered
        mSimulatedCommands.setImsRegistrationState(new int[]{1});

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_IMS_STATE_CHANGED, null));
        waitForMs(200);

        assertTrue(sst.isImsRegistered());

        // Simulate IMS unregistered
        mSimulatedCommands.setImsRegistrationState(new int[]{0});

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_IMS_STATE_CHANGED, null));
        waitForMs(200);

        assertFalse(sst.isImsRegistered());
    }

    @Test
    @MediumTest
    public void testSignalStrength() {
        SignalStrength ss = new SignalStrength(
                30, // gsmSignalStrength
                0,  // gsmBitErrorRate
                -1, // cdmaDbm
                -1, // cdmaEcio
                -1, // evdoDbm
                -1, // evdoEcio
                -1, // evdoSnr
                99, // lteSignalStrength
                SignalStrength.INVALID,     // lteRsrp
                SignalStrength.INVALID,     // lteRsrq
                SignalStrength.INVALID,     // lteRssnr
                SignalStrength.INVALID,     // lteCqi
                SignalStrength.INVALID,     // tdScdmaRscp
                true                        // gsmFlag
        );

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_SIGNAL_STRENGTH_UPDATE,
                new AsyncResult(null, ss, null)));
        waitForMs(200);
        assertEquals(sst.getSignalStrength(), ss);
    }

    @Test
    @MediumTest
    public void testGsmCellLocation() {

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_GET_LOC_DONE,
                new AsyncResult(null, new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9",
                        "10", "11", "12", "13", "14", "15"}, null)));

        waitForMs(200);
        GsmCellLocation cl = (GsmCellLocation) sst.getCellLocation();
        assertEquals(2, cl.getLac());
        assertEquals(3, cl.getCid());
    }
}
