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

package com.android.internal.telephony;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityLte;
import android.telephony.LteVopsSupportInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.TextUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DisplayInfoControllerTest extends TelephonyTest {
    private static final int PHONE_ID = 0;
    private static final String MCC = "600";
    private static final String MNC = "01";
    private static final String NUMERIC = MCC + MNC;
    private static final String NETWORK = "TestNet";

    private DisplayInfoController mDic;
    private ServiceStateTracker mSst;
    private ServiceStateTrackerTestHandler mSstHandler;
    private PersistableBundle mBundle;
    private CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;

    private class ServiceStateTrackerTestHandler extends HandlerThread {
        private ServiceStateTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            SignalStrengthController ssc = new SignalStrengthController(mPhone);
            doReturn(ssc).when(mPhone).getSignalStrengthController();
            doReturn(new ServiceState()).when(mPhone).getServiceState();
            doReturn(NUMERIC).when(mTelephonyManager).getSimOperatorNumericForPhone(eq(PHONE_ID));
            doReturn(NETWORK).when(mTelephonyManager).getSimOperatorNameForPhone(eq(PHONE_ID));

            // Capture listener registered for ServiceStateTracker to emulate the carrier config
            // change notification used later. In this test, it's the second one. The first one
            // comes from RatRatcheter.
            ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener>
                    listenerArgumentCaptor = ArgumentCaptor.forClass(
                    CarrierConfigManager.CarrierConfigChangeListener.class);
            mSst = new ServiceStateTracker(mPhone, mSimulatedCommands);
            verify(mCarrierConfigManager, atLeast(2)).registerCarrierConfigChangeListener(any(),
                    listenerArgumentCaptor.capture());
            mCarrierConfigChangeListener = listenerArgumentCaptor.getAllValues().get(1);
            doReturn(mSst).when(mPhone).getServiceStateTracker();
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DisplayInfoControllerTest setup!");
        super.setUp(getClass().getSimpleName());

        doReturn((Executor) Runnable::run).when(mContext).getMainExecutor();
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putBoolean(CarrierConfigManager.KEY_SHOW_ROAMING_INDICATOR_BOOL, true);
        mSstHandler = new ServiceStateTrackerTestHandler(getClass().getSimpleName());
        mSstHandler.start();
        waitUntilReady();
        waitForLastHandlerAction(mSstHandler.getThreadHandler());
    }

    @After
    public void tearDown() throws Exception {
        mSst.removeCallbacksAndMessages(null);
        mSst = null;
        mSstHandler.quit();
        mSstHandler.join();
        mSstHandler = null;
        mBundle = null;
        mCarrierConfigChangeListener = null;
        super.tearDown();
    }

    private void sendCarrierConfigUpdate() {
        mCarrierConfigChangeListener.onCarrierConfigChanged(PHONE_ID,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, TelephonyManager.UNKNOWN_CARRIER_ID,
                TelephonyManager.UNKNOWN_CARRIER_ID);
        waitForLastHandlerAction(mSstHandler.getThreadHandler());
    }

    private static String getPlmnFromCellIdentity(final CellIdentity ci) {
        if (ci == null || ci instanceof CellIdentityCdma) return "";

        final String mcc = ci.getMccString();
        final String mnc = ci.getMncString();

        if (TextUtils.isEmpty(mcc) || TextUtils.isEmpty(mnc)) return "";

        return mcc + mnc;
    }

    private void changeRegState(int state) {
        int voiceRat = TelephonyManager.NETWORK_TYPE_LTE;
        int dataRat = TelephonyManager.NETWORK_TYPE_LTE;
        CellIdentityLte cid =
                new CellIdentityLte(1, 1, 5, 1, new int[] {1, 2}, 5000,
                        MCC, MNC, NETWORK, NETWORK, Collections.emptyList(), null);
        LteVopsSupportInfo lteVopsSupportInfo =
                new LteVopsSupportInfo(
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED);
        waitForLastHandlerAction(mSstHandler.getThreadHandler());
        NetworkRegistrationInfo dataResult = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                state, dataRat, 0, false, null, cid, getPlmnFromCellIdentity(cid), 1, false, false,
                false, lteVopsSupportInfo);
        mSst.mPollingContext[0] = 3;
        final String[] oldOpNamesResult = new String[] {"test", "test", NUMERIC};
        mSst.sendMessage(
                mSst.obtainMessage(
                        ServiceStateTracker.EVENT_POLL_STATE_OPERATOR,
                        new AsyncResult(mSst.mPollingContext, oldOpNamesResult, null)));
        waitForLastHandlerAction(mSstHandler.getThreadHandler());
        // update data reg state to be in service
        mSst.sendMessage(
                mSst.obtainMessage(
                        ServiceStateTracker.EVENT_POLL_STATE_PS_CELLULAR_REGISTRATION,
                        new AsyncResult(mSst.mPollingContext, dataResult, null)));
        waitForLastHandlerAction(mSstHandler.getThreadHandler());
        NetworkRegistrationInfo voiceResult = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                state, voiceRat, 0, false, null, cid, getPlmnFromCellIdentity(cid), false, 0, 0, 0);
        mSst.sendMessage(
                mSst.obtainMessage(
                        ServiceStateTracker.EVENT_POLL_STATE_CS_CELLULAR_REGISTRATION,
                        new AsyncResult(mSst.mPollingContext, voiceResult, null)));
        waitForLastHandlerAction(mSstHandler.getThreadHandler());
    }

    @Test
    public void testIsRoamingOverride_NonRoamingOperator() {
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        mBundle.putStringArray(
                CarrierConfigManager.KEY_NON_ROAMING_OPERATOR_STRING_ARRAY, new String[] {NUMERIC});
        sendCarrierConfigUpdate();

        changeRegState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        ServiceState ss = mSst.getServiceState();

        assertFalse(ss.getRoaming()); // home

        doReturn(mSst).when(mPhone).getServiceStateTracker();
        mDic = new DisplayInfoController(mPhone);
        mDic.updateTelephonyDisplayInfo();
        TelephonyDisplayInfo tdi = mDic.getTelephonyDisplayInfo();

        assertFalse(tdi.isRoaming());
    }

    @Test
    public void testIsRoamingOverride_ForceHomeNetwork() {
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        mBundle.putBoolean(CarrierConfigManager.KEY_FORCE_HOME_NETWORK_BOOL, true);
        sendCarrierConfigUpdate();

        changeRegState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        ServiceState ss = mSst.getServiceState();

        assertFalse(ss.getRoaming()); // home

        doReturn(mSst).when(mPhone).getServiceStateTracker();
        mDic = new DisplayInfoController(mPhone);
        mDic.updateTelephonyDisplayInfo();
        TelephonyDisplayInfo tdi = mDic.getTelephonyDisplayInfo();

        assertFalse(tdi.isRoaming());
    }

    @Test
    public void testIsRoamingOverride_RoamingOperator() {
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        mBundle.putStringArray(
                CarrierConfigManager.KEY_ROAMING_OPERATOR_STRING_ARRAY, new String[] {"60101"});
        sendCarrierConfigUpdate();

        changeRegState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        ServiceState ss1 = mSst.getServiceState();

        assertTrue(ss1.getRoaming()); // roam

        doReturn(mSst).when(mPhone).getServiceStateTracker();
        mDic = new DisplayInfoController(mPhone);
        mDic.updateTelephonyDisplayInfo();
        TelephonyDisplayInfo tdi = mDic.getTelephonyDisplayInfo();

        assertTrue(tdi.isRoaming());
    }

    @Test
    public void testIsRoamingOverride_NonRoamingGsmOperator() {
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        mBundle.putStringArray(
                CarrierConfigManager.KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY,
                new String[] {NUMERIC});
        sendCarrierConfigUpdate();

        changeRegState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        ServiceState ss = mSst.getServiceState();

        assertFalse(ss.getRoaming()); // home

        doReturn(mSst).when(mPhone).getServiceStateTracker();
        mDic = new DisplayInfoController(mPhone);
        mDic.updateTelephonyDisplayInfo();
        TelephonyDisplayInfo tdi = mDic.getTelephonyDisplayInfo();

        assertFalse(tdi.isRoaming());
    }

    @Test
    public void testIsRoamingOverride_RoamingGsmOperator() {
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        mBundle.putStringArray(
                CarrierConfigManager.KEY_GSM_ROAMING_NETWORKS_STRING_ARRAY, new String[] {NUMERIC});
        sendCarrierConfigUpdate();

        changeRegState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        ServiceState ss1 = mSst.getServiceState();

        assertTrue(ss1.getRoaming()); // roam

        doReturn(mSst).when(mPhone).getServiceStateTracker();
        mDic = new DisplayInfoController(mPhone);
        mDic.updateTelephonyDisplayInfo();
        TelephonyDisplayInfo tdi = mDic.getTelephonyDisplayInfo();

        assertTrue(tdi.isRoaming());
    }

    @Test
    public void testIsRoamingOverride_HideRoamingIndicator() {
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mBundle.putStringArray(
                CarrierConfigManager.KEY_GSM_ROAMING_NETWORKS_STRING_ARRAY, new String[] {NUMERIC});
        mBundle.putBoolean(CarrierConfigManager.KEY_SHOW_ROAMING_INDICATOR_BOOL, false);
        sendCarrierConfigUpdate();

        changeRegState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        ServiceState ss1 = mSst.getServiceState();

        assertTrue(ss1.getRoaming()); // roam

        doReturn(mSst).when(mPhone).getServiceStateTracker();
        mDic = new DisplayInfoController(mPhone);
        mDic.updateTelephonyDisplayInfo();
        TelephonyDisplayInfo tdi = mDic.getTelephonyDisplayInfo();

        assertFalse(tdi.isRoaming()); // display override
    }
}
