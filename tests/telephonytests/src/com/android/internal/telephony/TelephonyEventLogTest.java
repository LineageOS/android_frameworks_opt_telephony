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

import com.android.ims.ImsConfig;
import com.android.ims.ImsReasonInfo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.test.mock.MockContext;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

public class TelephonyEventLogTest {
    private static final String TAG = "TelephonyEventLogTest";

    @Mock
    ITelephonyDebug.Stub mBinder;

    private EventLogContext mContext;
    private TelephonyEventLog mEventLog;

    private class EventLogContext extends MockContext {

        ITelephonyDebug.Stub mBinder;

        private EventLogContext(ITelephonyDebug.Stub binder) {
            mBinder = binder;
        }

        @Override
        public boolean bindService(Intent serviceIntent, ServiceConnection connection, int flags) {
            connection.onServiceConnected(new ComponentName("test", "test"), mBinder);
            return true;
        }

        @Override
        public String getPackageName() {
            return "com.android.internal.telephony";
        }
    }

    private static final class BundleMatcher extends BaseMatcher<Bundle> {
        ArrayMap<String, Object> mMap = null;

        public BundleMatcher(ArrayMap<String, Object> m) {
            mMap = m;
        }

        @Override
        public boolean matches(Object item) {
            Bundle b = (Bundle) item;

            // compare only values stored in the map
            for (int i=0; i < mMap.size(); i++) {
                String key = mMap.keyAt(i);
                Object value = mMap.valueAt(i);
                if (!value.equals(b.get(key))) {
                    logd("key: " + key + ", expected: " + value + ", actual: " + b.get(key));
                    return false;
                }
            }

            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(mMap.toString());
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = new EventLogContext(mBinder);

        doReturn(mBinder).when(mBinder).
                queryLocalInterface(eq("com.android.internal.telephony.ITelephonyDebug"));

        //Use reflection to modify singleton
        Field field = TelephonyEventLog.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        SparseArray<TelephonyEventLog> instances = (SparseArray<TelephonyEventLog>)field.get(null);
        instances.clear();

        mEventLog = TelephonyEventLog.getInstance(mContext, 0);
    }

    @After
    public void tearDown() throws Exception {
        mEventLog = null;
    }

    @Test @SmallTest
    public void testWriteServiceStateChanged() {
        ServiceState serviceState = new ServiceState();
        serviceState.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        serviceState.setDataRegState(ServiceState.STATE_IN_SERVICE);
        serviceState.setVoiceRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        serviceState.setDataRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        serviceState.setVoiceOperatorName("Test Voice Long", "TestVoice", "12345");
        serviceState.setDataOperatorName("Test Date Long", "TestData", "67890");
        serviceState.setRilVoiceRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
        serviceState.setRilDataRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        mEventLog.writeServiceStateChanged(serviceState);

        ArrayMap<String, Object> m = new ArrayMap<>();
        m.put("voiceRegState", ServiceState.STATE_IN_SERVICE);
        m.put("dataRegState", ServiceState.STATE_IN_SERVICE);
        m.put("voiceRoamingType", ServiceState.ROAMING_TYPE_NOT_ROAMING);
        m.put("dataRoamingType", ServiceState.ROAMING_TYPE_NOT_ROAMING);
        m.put("operator-alpha-long", "Test Voice Long");
        m.put("operator-alpha-short", "TestVoice");
        m.put("operator-numeric", "12345");
        m.put("data-operator-alpha-long", "Test Date Long");
        m.put("data-operator-alpha-short", "TestData");
        m.put("data-operator-numeric", "67890");
        m.put("radioTechnology", ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
        m.put("dataRadioTechnology", ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SERVICE_STATE), eq(-1), eq(-1),
                    argThat(new BundleMatcher(m)));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteSetAirplaneMode() {
        mEventLog.writeSetAirplaneMode(true);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_AIRPLANE_MODE), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeSetAirplaneMode(false);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_AIRPLANE_MODE), eq(0),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteSetCellDataEnabled() {
        mEventLog.writeSetCellDataEnabled(true);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_CELL_DATA_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeSetCellDataEnabled(false);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_CELL_DATA_ENABLED), eq(0),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteSetDataRoamingEnabled() {
        mEventLog.writeSetDataRoamingEnabled(true);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_DATA_ROAMING_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeSetDataRoamingEnabled(false);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_DATA_ROAMING_ENABLED), eq(0),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteSetPreferredNetworkType() {
        mEventLog.writeSetPreferredNetworkType(RILConstants.NETWORK_MODE_GLOBAL);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_PREFERRED_NETWORK_MODE),
                    eq(RILConstants.NETWORK_MODE_GLOBAL),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteSetWifiEnabled() {
        mEventLog.writeSetWifiEnabled(true);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_WIFI_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeSetWifiEnabled(false);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_WIFI_ENABLED), eq(0),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteSetWfcMode() {
        mEventLog.writeSetWfcMode(ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_WFC_MODE),
                    eq(ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteImsSetFeatureValue() {
        mEventLog.writeImsSetFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE,
                TelephonyManager.NETWORK_TYPE_LTE, 1, ImsConfig.OperationStatusConstants.SUCCESS);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_VO_LTE_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeImsSetFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI,
                TelephonyManager.NETWORK_TYPE_IWLAN, 1, ImsConfig.OperationStatusConstants.SUCCESS);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_VO_WIFI_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeImsSetFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE,
                TelephonyManager.NETWORK_TYPE_LTE, 1, ImsConfig.OperationStatusConstants.SUCCESS);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_VI_LTE_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeImsSetFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI,
                TelephonyManager.NETWORK_TYPE_IWLAN, 1, ImsConfig.OperationStatusConstants.SUCCESS);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_SETTINGS),
                    eq(TelephonyEventLog.SETTING_VI_WIFI_ENABLED), eq(1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    @Test @SmallTest
    public void testWriteImsConnectionState() {
        mEventLog.writeOnImsConnectionState(
                TelephonyEventLog.IMS_CONNECTION_STATE_CONNECTED, null);

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_IMS_CONNECTION_STATE),
                    eq(TelephonyEventLog.IMS_CONNECTION_STATE_CONNECTED),
                    eq(-1),
                    isNull(Bundle.class));
        } catch (RemoteException e) {
            fail(e.toString());
        }

        mEventLog.writeOnImsConnectionState(
                TelephonyEventLog.IMS_CONNECTION_STATE_DISCONNECTED,
                new ImsReasonInfo(1, 2, "test"));

        ArrayMap<String, Object> m = new ArrayMap<>();
        m.put(TelephonyEventLog.DATA_KEY_REASONINFO_CODE, 1);
        m.put(TelephonyEventLog.DATA_KEY_REASONINFO_EXTRA_CODE, 2);
        m.put(TelephonyEventLog.DATA_KEY_REASONINFO_EXTRA_MESSAGE, "test");

        try {
            verify(mContext.mBinder).writeEvent(anyLong(), eq(0),
                    eq(TelephonyEventLog.TAG_IMS_CONNECTION_STATE),
                    eq(TelephonyEventLog.IMS_CONNECTION_STATE_DISCONNECTED),
                    eq(-1),
                    argThat(new BundleMatcher(m)));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}