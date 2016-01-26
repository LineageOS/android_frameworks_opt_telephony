/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.HashMap;

public class ServiceStateTrackerTest {
    private static final String TAG = "ServiceStateTrackerTest";

    private static final String AMERICA_LA_TIME_ZONE = "America/Los_Angeles";
    private static final String KEY_TIME_ZONE = "time-zone";

    @Mock
    private GsmCdmaPhone mPhone;
    @Mock
    private UiccController mUiccController;
    @Mock
    private UiccCardApplication m3GPPUiccApp;
    @Mock
    private SIMRecords mSimRecords;
    @Mock
    private SubscriptionController mSubscriptionController;
    @Mock
    private SparseArray<TelephonyEventLog> mLogInstances;
    @Mock
    private TelephonyEventLog mTelephonyEventLog;
    @Mock
    private DcTracker mDct;
    @Mock
    private ProxyController mProxyController;
    @Mock
    HashMap<String, IBinder> mServiceCache;
    @Mock
    IBinder mBinder;

    private SimulatedCommands simulatedCommands;
    private ContextFixture mContextFixture;
    private ServiceStateTracker sst;

    private Object mLock = new Object();
    private boolean mReady = false;

    private class ServiceStateTrackerTestHandler extends HandlerThread {

        private ServiceStateTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            sst = new ServiceStateTracker(mPhone, simulatedCommands);
            synchronized (mLock) {
                mReady = true;
            }
        }
    }

    private void waitUntilReady() {
        while(true) {
            synchronized (mLock) {
                if (mReady) {
                    break;
                }
            }
        }
    }

    @Before
    public void setUp() throws Exception {

        logd("ServiceStateTrackerTest +Setup!");
        MockitoAnnotations.initMocks(this);
        mContextFixture = new ContextFixture();
        simulatedCommands = new SimulatedCommands();

        doReturn(mContextFixture.getTestDouble()).when(mPhone).getContext();
        doReturn(true).when(mPhone).getUnitTestMode();
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        doReturn(true).when(mDct).isDisconnected();
        doReturn(m3GPPUiccApp).when(mUiccController).getUiccCardApplication(
                anyInt(), eq(UiccController.APP_FAM_3GPP));
        doReturn(mSimRecords).when(m3GPPUiccApp).getIccRecords();
        mPhone.mDcTracker = mDct;

        //Use reflection to mock singleton
        Field field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSubscriptionController);

        //Use reflection to mock singleton
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        // Replace ServiceManager.sCache so ServiceManager.getService can return a mocked
        // IBinder.
        field = ServiceManager.class.getDeclaredField("sCache");
        field.setAccessible(true);
        field.set(null, mServiceCache);

        doReturn(mTelephonyEventLog).when(mLogInstances).get(anyInt());
        doReturn(mBinder).when(mServiceCache).get(Context.ALARM_SERVICE);

        // Use reflection to replace TelephonyEventLog.sInstances with our mocked mLogInstances
        field = TelephonyEventLog.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        field.set(null, mLogInstances);

        field = ProxyController.class.getDeclaredField("sProxyController");
        field.setAccessible(true);
        field.set(null, mProxyController);

        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_sameNamedOperatorConsideredRoaming,
                new String[]{"123456"});

        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_operatorConsideredNonRoaming,
                new String[]{"123456"});

        int dds = SubscriptionManager.getDefaultDataSubId();
        doReturn(dds).when(mPhone).getSubId();

        mReady = false;
        new ServiceStateTrackerTestHandler(TAG).start();
        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        sst = null;
    }

    @Test
    @SmallTest
    public void testSetRadioPower() {
        waitUntilReady();

        boolean oldState = simulatedCommands.getRadioState().isOn();
        sst.setRadioPower(!oldState);
        waitForMs(100);
        assertTrue(oldState != simulatedCommands.getRadioState().isOn());
    }

    @Test
    @SmallTest
    public void testSpnUpdateShowPlmnOnly() {
        waitUntilReady();

        doReturn(0x02).when(mSimRecords).getDisplayRule(anyString());
        doReturn(IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN).when(m3GPPUiccApp).getState();

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_NETWORK_STATE_CHANGED, null));

        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextFixture.getTestDouble(), times(1)).
                sendStickyBroadcastAsUser(intentArgumentCaptor.capture(), eq(UserHandle.ALL));

        // We only want to verify the intent SPN_STRINGS_UPDATED_ACTION.
        Intent intent = intentArgumentCaptor.getAllValues().get(0);
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
    }


    @Test
    @SmallTest
    public void testNITZupdate() {
        waitUntilReady();

        doReturn(0x02).when(mSimRecords).getDisplayRule(anyString());

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_NITZ_TIME,
                new AsyncResult(null,
                        new Object[]{"16/01/22,23:24:44-32,00", Long.valueOf(41824)}, null)));

        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextFixture.getTestDouble(), atLeast(1)).
                sendStickyBroadcastAsUser(intentArgumentCaptor.capture(), eq(UserHandle.ALL));

        boolean receivedExpectedIntent = false;
        for(Intent intent : intentArgumentCaptor.getAllValues()) {
            if(intent.getAction().equals(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE)) {
                assertEquals(Intent.FLAG_RECEIVER_REPLACE_PENDING, intent.getFlags());
                assertEquals(AMERICA_LA_TIME_ZONE, intent.getExtras().getString(KEY_TIME_ZONE));
                receivedExpectedIntent = true;
            }
        }
        assertTrue(receivedExpectedIntent);
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
