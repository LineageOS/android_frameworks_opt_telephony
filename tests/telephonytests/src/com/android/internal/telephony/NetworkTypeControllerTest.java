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
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PcoData;
import android.telephony.PhysicalChannelConfig;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NetworkTypeControllerTest extends TelephonyTest {
    // private constants copied over from NetworkTypeController
    private static final int EVENT_DATA_RAT_CHANGED = 2;
    private static final int EVENT_NR_STATE_CHANGED = 3;
    private static final int EVENT_NR_FREQUENCY_CHANGED = 4;
    private static final int EVENT_PHYSICAL_LINK_STATUS_CHANGED = 5;
    private static final int EVENT_PHYSICAL_CHANNEL_CONFIG_NOTIF_CHANGED = 6;
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 7;
    private static final int EVENT_PRIMARY_TIMER_EXPIRED = 8;
    private static final int EVENT_SECONDARY_TIMER_EXPIRED = 9;
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 10;
    private static final int EVENT_PREFERRED_NETWORK_MODE_CHANGED = 11;
    private static final int EVENT_INITIALIZE = 12;
    private static final int EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED = 13;
    private static final int EVENT_PCO_DATA_CHANGED = 14;

    private NetworkTypeController mNetworkTypeController;
    private PersistableBundle mBundle;

    // Mocked classes
    DataConnection mDataConnection;
    ApnSetting mApnSetting;

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
        mDataConnection = mock(DataConnection.class);
        mApnSetting = mock(ApnSetting.class);
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_CONFIGURATION_STRING,
                "connected_mmwave:5G_Plus,connected:5G,not_restricted_rrc_idle:5G,"
                        + "not_restricted_rrc_con:5G");
        broadcastCarrierConfigs();

        replaceInstance(Handler.class, "mLooper", mDisplayInfoController, Looper.myLooper());
        doReturn(RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA)).when(
                mPhone).getCachedAllowedNetworkTypesBitmask();
        doReturn(false).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        doReturn(new int[] {0}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mNetworkTypeController.getHandler().removeCallbacksAndMessages(null);
        mNetworkTypeController = null;
        mBundle = null;
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testUpdateOverrideNetworkTypeNrSa() throws Exception {
        // not NR
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // NR SA connected
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        updateOverrideNetworkType();

        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testUpdateOverrideNetworkTypeNrSaMmwave() throws Exception {
        // not NR
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // NR SA connected and millimeter wave frequency
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();

        updateOverrideNetworkType();

        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateIdleSupportPhysicalChannelConfig1_6() throws Exception {
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        setPhysicalLinkStatus(false);
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateIdle_usingUserDataForRrcDetection() throws Exception {
        mBundle.putBoolean(
                CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL, true);
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        broadcastCarrierConfigs();
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);

        processAllMessages();

        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateLteConnected() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateLteConnectedSupportPhysicalChannelConfig1_6()
            throws Exception {
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        broadcastCarrierConfigs();
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        setPhysicalLinkStatus(true);
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateLteConnected_usingUserDataForRrcDetection()
            throws Exception {
        mBundle.putBoolean(
                CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL, true);
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        broadcastCarrierConfigs();
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
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
    public void testTransitionToCurrentStateNrConnectedMmwaveWithAdditionalBandAndNoMmwave()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_HIGH).when(mServiceState).getNrFrequencyRange();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41});
        PhysicalChannelConfig physicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setBand(41)
                .build();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(physicalChannelConfig);
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNoAdditionalBandAndNoMmwave()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_HIGH).when(mServiceState).getNrFrequencyRange();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41});
        PhysicalChannelConfig physicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setBand(2)
                .build();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(physicalChannelConfig);
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNrAdvancedCapable() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithPcoAndNoNrAdvancedCapable()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();
        int cid = 1;
        byte[] contents = new byte[]{0};
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByContextId(cid);
        doReturn(mApnSetting).when(mDataConnection).getApnSetting();
        doReturn(true).when(mApnSetting).canHandleType(ApnSetting.TYPE_DEFAULT);
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();


        mNetworkTypeController.sendMessage(EVENT_PCO_DATA_CHANGED,
                new AsyncResult(null, new PcoData(cid, "", 0xff03, contents), null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithPcoLength4AndNoNrAdvancedCapable()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();
        int cid = 1;
        byte[] contents = new byte[]{31, 1, 84, 0};
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByContextId(cid);
        doReturn(mApnSetting).when(mDataConnection).getApnSetting();
        doReturn(true).when(mApnSetting).canHandleType(ApnSetting.TYPE_DEFAULT);
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();


        mNetworkTypeController.sendMessage(EVENT_PCO_DATA_CHANGED,
                new AsyncResult(null, new PcoData(cid, "", 0xff03, contents), null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithWrongPcoAndNoNrAdvancedCapable()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();
        int cid = 1;
        byte[] contents = new byte[]{1};
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByContextId(cid);
        doReturn(mApnSetting).when(mDataConnection).getApnSetting();
        doReturn(true).when(mApnSetting).canHandleType(ApnSetting.TYPE_DEFAULT);
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF00);
        broadcastCarrierConfigs();


        mNetworkTypeController.sendMessage(EVENT_PCO_DATA_CHANGED,
                new AsyncResult(null, new PcoData(cid, "", 0xff03, contents), null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNrAdvancedCapableAndPco()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        int cid = 1;
        byte[] contents = new byte[]{1};
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByContextId(cid);
        doReturn(mApnSetting).when(mDataConnection).getApnSetting();
        doReturn(true).when(mApnSetting).canHandleType(ApnSetting.TYPE_DEFAULT);
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(EVENT_PCO_DATA_CHANGED,
                new AsyncResult(null, new PcoData(cid, "", 0xff03, contents), null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNrAdvancedCapableAndPcoLength4()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        int cid = 1;
        byte[] contents = new byte[]{31, 1, 84, 1};
        doReturn(mDataConnection).when(mDcTracker).getDataConnectionByContextId(cid);
        doReturn(mApnSetting).when(mDataConnection).getApnSetting();
        doReturn(true).when(mApnSetting).canHandleType(ApnSetting.TYPE_DEFAULT);
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(EVENT_PCO_DATA_CHANGED,
                new AsyncResult(null, new PcoData(cid, "", 0xff03, contents), null));
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
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testNrPhysicalChannelChange1_6FromNrConnectedMmwaveToLteConnected()
            throws Exception {
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        processAllMessages();
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        setPhysicalLinkStatus(true);
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);

        processAllMessages();

        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }


    @Test
    public void testUsingUserDataForRrcDetection_FromNrConnectedMmwaveToLteConnected()
            throws Exception {
        mBundle.putBoolean(
                CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL, true);
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        broadcastCarrierConfigs();
        processAllMessages();
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);

        processAllMessages();

        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelChangeFromLteToLteCaInLegacyState() throws Exception {
        testTransitionToCurrentStateLegacy();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {30000}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        processAllMessages();

        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testEventPhysicalChannelChangeFromLteToLteCaInLteConnectedState() throws Exception {
        // Remove RRC idle/RRC connected from 5G override
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_CONFIGURATION_STRING,
                "connected_mmwave:5G_Plus,connected:5G");
        broadcastCarrierConfigs();

        // Transition to LTE connected state
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE -> LTE+
        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {30000}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        processAllMessages();

        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testEventPhysicalChannelChangeFromLteToLteCaInIdleState() throws Exception {
        // Remove RRC idle/RRC connected from 5G override
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_CONFIGURATION_STRING,
                "connected_mmwave:5G_Plus,connected:5G");
        broadcastCarrierConfigs();

        // Transition to idle state
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));
        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE -> LTE+
        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {30000}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        processAllMessages();

        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testEventPhysicalLinkStatusChanged() throws Exception {
        testTransitionToCurrentStateLteConnected();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));

        processAllMessages();

        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalLinkStatusChangedSupportPhysicalChannelConfig1_6()
            throws Exception {
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        processAllMessages();
        testTransitionToCurrentStateLteConnectedSupportPhysicalChannelConfig1_6();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        setPhysicalLinkStatus(false);
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);

        processAllMessages();

        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalLinkStatusChanged_UsingUserDataForRrcDetection()
            throws Exception {
        mBundle.putBoolean(
                CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL, true);
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        broadcastCarrierConfigs();
        processAllMessages();
        testTransitionToCurrentStateLteConnected_usingUserDataForRrcDetection();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_PHYSICAL_LINK_STATUS_CHANGED,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));

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
        doReturn(RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA)).when(
                mPhone).getCachedAllowedNetworkTypesBitmask();

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
    public void testPrimaryTimerDeviceIdleMode() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        broadcastCarrierConfigs();

        IPowerManager powerManager = mock(IPowerManager.class);
        PowerManager pm = new PowerManager(mContext, powerManager, mock(IThermalService.class),
                new Handler(Looper.myLooper()));
        doReturn(pm).when(mContext).getSystemService(Context.POWER_SERVICE);
        doReturn(true).when(powerManager).isDeviceIdleMode();
        mNetworkTypeController.sendMessage(17 /*EVENT_DEVICE_IDLE_MODE_CHANGED*/);

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should not trigger timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
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
    public void testPrimaryTimerReset_theNetworkModeWithoutNr() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        broadcastCarrierConfigs();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // remove NR from preferred network types
        doReturn(RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA)).when(
                mPhone).getCachedAllowedNetworkTypesBitmask();

        // trigger 10 second timer after disconnecting from NR, and then it does the timer reset
        // since the network mode without the NR capability.
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(EVENT_NR_STATE_CHANGED);
        processAllMessages();

        // timer should be reset.
        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // trigger 10 second timer after disconnecting from NR
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.is5GHysteresisActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(EVENT_NR_FREQUENCY_CHANGED);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
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

    private void setPhysicalLinkStatus(Boolean state) {
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        // If PhysicalChannelConfigList is empty, PhysicalLinkStatus is DcController
        // .PHYSICAL_LINK_NOT_ACTIVE
        // If PhysicalChannelConfigList is not empty, PhysicalLinkStatus is DcController
        // .PHYSICAL_LINK_ACTIVE

        if (state) {
            PhysicalChannelConfig physicalChannelConfig = new PhysicalChannelConfig.Builder()
                    .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                    .setBand(41)
                    .build();
            lastPhysicalChannelConfigList.add(physicalChannelConfig);
        }
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithLowBandwidth() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        doReturn(new int[] {19999}).when(mServiceState).getCellBandwidths();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithHighBandwidth() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setCellBandwidthDownlinkKhz(20001)
                .build());
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithHighBandwidthIncludingLte()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setCellBandwidthDownlinkKhz(20000)
                .build());
        lastPhysicalChannelConfigList.add(new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCellBandwidthDownlinkKhz(10000)
                .build());
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        mBundle.putBoolean(
                CarrierConfigManager.KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH_BOOL,
                true);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testNrAdvancedDisabledWhileRoaming() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putBoolean(CarrierConfigManager.KEY_ENABLE_NR_ADVANCED_WHILE_ROAMING_BOOL, false);
        broadcastCarrierConfigs();

        mNetworkTypeController.sendMessage(NetworkTypeController.EVENT_UPDATE);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }
}
