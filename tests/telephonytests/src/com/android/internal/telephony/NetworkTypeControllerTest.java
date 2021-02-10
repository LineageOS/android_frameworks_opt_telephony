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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.dataconnection.DcController;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NetworkTypeControllerTest extends TelephonyTest {
    // private constants copied over from NetworkTypeController
    private static final int EVENT_DATA_RAT_CHANGED = 2;
    private static final int EVENT_NR_STATE_CHANGED = 3;
    private static final int EVENT_NR_FREQUENCY_CHANGED = 4;
    private static final int EVENT_PHYSICAL_LINK_STATE_CHANGED = 5;
    private static final int EVENT_PHYSICAL_CHANNEL_CONFIG_NOTIF_CHANGED = 6;
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 7;
    private static final int EVENT_PRIMARY_TIMER_EXPIRED = 8;
    private static final int EVENT_SECONDARY_TIMER_EXPIRED = 9;
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 10;
    private static final int EVENT_PREFERRED_NETWORK_MODE_CHANGED = 11;
    private static final int EVENT_INITIALIZE = 12;

    private NetworkTypeController mNetworkTypeController;
    private PersistableBundle mBundle;

    private IState getCurrentState() throws Exception {
        Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
        method.setAccessible(true);
        return (IState) method.invoke(mNetworkTypeController);
    }

    private void updateOverrideNetworkType() throws Exception {
        Method method = NetworkTypeController.class.getDeclaredMethod("updateOverrideNetworkType");
        method.setAccessible(true);
        method.invoke(mNetworkTypeController);
    }

    private void broadcastCarrierConfigs() {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        mContext.sendBroadcast(intent);
        processAllMessages();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_CONFIGURATION_STRING,
                "connected_mmwave:5G_Plus,connected:5G,not_restricted_rrc_idle:5G,"
                        + "not_restricted_rrc_con:5G");
        broadcastCarrierConfigs();

        replaceInstance(Handler.class, "mLooper", mDisplayInfoController, Looper.myLooper());
        doReturn(TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA).when(mPhone)
                .getCachedPreferredNetworkType();
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mNetworkTypeController.getHandler().removeCallbacksAndMessages(null);
        mNetworkTypeController = null;
        super.tearDown();
    }

    @Test
    public void testUpdateOverrideNetworkTypeNrNsa() throws Exception {
        // not NR
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // NR NSA, restricted
        doReturn(NetworkRegistrationInfo.NR_STATE_RESTRICTED).when(mServiceState).getNrState();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // NR NSA, not restricted
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // NR NSA, sub 6 frequency
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // NR NSA, millimeter wave frequency
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testUpdateOverrideNetworkTypeLte() throws Exception {
        // normal LTE
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE CA at bandwidth threshold
        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {20000}).when(mServiceState).getCellBandwidths();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE CA above bandwidth threshold
        doReturn(new int[] {20000, 1400}).when(mServiceState).getCellBandwidths();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE ADVANCED PRO
        doReturn("test_patternShowAdvanced").when(mServiceState).getOperatorAlphaLongRaw();
        mBundle.putString(CarrierConfigManager.KEY_SHOW_CARRIER_DATA_ICON_PATTERN_STRING,
                ".*_patternShowAdvanced");
        broadcastCarrierConfigs();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testUpdateOverrideNetworkType() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_HSPAP)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(nri).when(mServiceState).getNetworkRegistrationInfo(anyInt(), anyInt());
        updateOverrideNetworkType();

        // override shouldn't be NR if not on LTE despite NR_STATE_CONNECTED
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testTransitionToCurrentStateLegacy() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_HSPAP).when(mServiceState).getDataNetworkType();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("legacy", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateRestricted() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_RESTRICTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("restricted", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateIdle() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATE_CHANGED,
                new AsyncResult(null, DcController.PHYSICAL_LINK_NOT_ACTIVE, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateLteConnected() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATE_CHANGED,
                new AsyncResult(null, DcController.PHYSICAL_LINK_ACTIVE, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnected() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedMmwave() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testEventDataRatChanged() throws Exception {
        testTransitionToCurrentStateLegacy();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(EVENT_DATA_RAT_CHANGED);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testEventNrStateChanged() throws Exception {
        testTransitionToCurrentStateNrConnected();
        doReturn(NetworkRegistrationInfo.NR_STATE_RESTRICTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();
        assertEquals("restricted", getCurrentState().getName());
    }

    @Test
    public void testEventNrFrequencyRangeChangedFromNrConnectedMmwaveToNrConnected()
            throws Exception {
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();

        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testEventNrFrequencyRangeChangedFromNrConnectedToNrConnectedMmwave()
            throws Exception {
        testTransitionToCurrentStateNrConnected();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();

        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testNrPhysicalChannelChangeFromNrConnectedMmwaveToLteConnected() throws Exception {
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATE_CHANGED,
                new AsyncResult(null, DcController.PHYSICAL_LINK_ACTIVE, null));
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalLinkStateChanged() throws Exception {
        testTransitionToCurrentStateLteConnected();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATE_CHANGED,
                new AsyncResult(null, DcController.PHYSICAL_LINK_NOT_ACTIVE, null));
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelConfigNotifChanged() throws Exception {
        testTransitionToCurrentStateNrConnected();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_NOTIF_CHANGED,
                new AsyncResult(null, false, null));
        processAllMessages();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testEventRadioOffOrUnavailable() throws Exception {
        testTransitionToCurrentStateNrConnected();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();

        mNetworkTypeController.sendMessage(EVENT_RADIO_OFF_OR_UNAVAILABLE);
        processAllMessages();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testEventPreferredNetworkModeChanged() throws Exception {
        testTransitionToCurrentStateNrConnected();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // remove NR from preferred network types
        doReturn(TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA).when(mPhone)
                .getCachedPreferredNetworkType();

        mNetworkTypeController.sendMessage(EVENT_PREFERRED_NETWORK_MODE_CHANGED);
        processAllMessages();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testPrimaryTimerExpire() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        broadcastCarrierConfigs();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testPrimaryTimerReset() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        broadcastCarrierConfigs();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // trigger 10 second timer after disconnecting from NR
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // reconnect to NR in the middle of the timer
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testPrimaryTimerExpireMmwave() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        broadcastCarrierConfigs();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testPrimaryTimerResetMmwave() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        broadcastCarrierConfigs();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());

        // trigger 10 second timer after disconnecting from NR
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // reconnect to NR in the middle of the timer
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testSecondaryTimerExpire() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected,any,30");
        broadcastCarrierConfigs();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testSecondaryTimerReset() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected,any,30");
        broadcastCarrierConfigs();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // reconnect to NR in the middle of the timer
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testSecondaryTimerExpireMmwave() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        broadcastCarrierConfigs();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testSecondaryTimerResetMmwave() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        broadcastCarrierConfigs();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // reconnect to NR in the middle of the timer
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }

    @Test
    public void testNrTimerResetIn3g() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        broadcastCarrierConfigs();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // rat is UMTS, should stop timer
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(nri).when(mServiceState).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_DATA_RAT_CHANGED);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.is5GHysteresisActive());
    }
}
