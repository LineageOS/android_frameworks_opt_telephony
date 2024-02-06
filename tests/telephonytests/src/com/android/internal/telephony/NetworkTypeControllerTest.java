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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhysicalChannelConfig;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.DataCallResponse;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NetworkTypeControllerTest extends TelephonyTest {
    private NetworkTypeController mNetworkTypeController;
    private PersistableBundle mBundle;
    private CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;

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

    private void sendCarrierConfigChanged() {
        if (mCarrierConfigChangeListener != null) {
            mCarrierConfigChangeListener.onCarrierConfigChanged(mPhone.getPhoneId(),
                    mPhone.getSubId(), TelephonyManager.UNKNOWN_CARRIER_ID,
                    TelephonyManager.UNKNOWN_CARRIER_ID);
        }
        processAllMessages();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_CONFIGURATION_STRING,
                "connected_mmwave:5G_Plus,connected:5G,not_restricted_rrc_idle:5G,"
                        + "not_restricted_rrc_con:5G");
        mBundle.putInt(CarrierConfigManager.KEY_LTE_PLUS_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        sendCarrierConfigChanged();

        replaceInstance(Handler.class, "mLooper", mDisplayInfoController, Looper.myLooper());
        doReturn(RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA)).when(
                mPhone).getCachedAllowedNetworkTypesBitmask();
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        doReturn(new int[] {0}).when(mServiceState).getCellBandwidths();
        // Capture listener to emulate the carrier config change notification used later
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        processAllMessages();
        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                listenerArgumentCaptor.capture());
        mCarrierConfigChangeListener = listenerArgumentCaptor.getAllValues().get(0);
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
        sendCarrierConfigChanged();
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
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("legacy", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateRestricted() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("restricted", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateIdle() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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

        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        setPhysicalLinkStatus(false);
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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
        sendCarrierConfigChanged();
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());

        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateLteConnected() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateLteConnectedSupportPhysicalChannelConfig1_6()
            throws Exception {
        doReturn(true).when(mTelephonyManager).isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
        mNetworkTypeController = new NetworkTypeController(mPhone, mDisplayInfoController);
        sendCarrierConfigChanged();
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());

        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        setPhysicalLinkStatus(true);
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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
        sendCarrierConfigChanged();
        processAllMessages();
        assertEquals("DefaultState", getCurrentState().getName());

        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnected() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedMmwave() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedMmwaveWithAdditionalBandAndNoMmwave()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_HIGH).when(mServiceState).getNrFrequencyRange();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41});
        PhysicalChannelConfig physicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(1)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .setBand(41)
                .build();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(physicalChannelConfig);
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        sendCarrierConfigChanged();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedMmwaveWithAdditionalBandAndNoMmwaveNrNsa()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_HIGH).when(mServiceState).getNrFrequencyRange();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41});
        PhysicalChannelConfig ltePhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(1)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .build();
        PhysicalChannelConfig nrPhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(2)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setCellConnectionStatus(CellInfo.CONNECTION_SECONDARY_SERVING)
                .setBand(41)
                .build();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(ltePhysicalChannelConfig);
        lastPhysicalChannelConfigList.add(nrPhysicalChannelConfig);
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        sendCarrierConfigChanged();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNoAdditionalBandAndNoMmwave()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
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
        sendCarrierConfigChanged();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNrAdvancedCapable() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        sendCarrierConfigChanged();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithPcoAndNoNrAdvancedCapable()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        sendCarrierConfigChanged();

        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController).registerDataNetworkControllerCallback(
                dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback callback = dataNetworkControllerCallbackCaptor.getValue();
        callback.onNrAdvancedCapableByPcoChanged(false);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithWrongPcoAndNoNrAdvancedCapable()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF00);
        sendCarrierConfigChanged();

        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController).registerDataNetworkControllerCallback(
                dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback callback = dataNetworkControllerCallbackCaptor.getValue();
        callback.onNrAdvancedCapableByPcoChanged(false);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithNrAdvancedCapableAndPco()
            throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 0xFF03);
        sendCarrierConfigChanged();

        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController).registerDataNetworkControllerCallback(
                dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback callback = dataNetworkControllerCallbackCaptor.getValue();
        callback.onNrAdvancedCapableByPcoChanged(true);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testEventDataRatChanged() throws Exception {
        testTransitionToCurrentStateLegacy();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testEventNrStateChanged() throws Exception {
        testTransitionToCurrentStateNrConnected();
        doReturn(NetworkRegistrationInfo.NR_STATE_RESTRICTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("restricted", getCurrentState().getName());
    }

    @Test
    public void testEventNrFrequencyRangeChangedFromNrConnectedMmwaveToNrConnected()
            throws Exception {
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testEventNrFrequencyRangeChangedFromNrConnectedToNrConnectedMmwave()
            throws Exception {
        testTransitionToCurrentStateNrConnected();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelConfigChangedWithRatcheting() throws Exception {
        testTransitionToCurrentStateNrConnected();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41, 77});
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        mBundle.putBoolean(CarrierConfigManager.KEY_RATCHET_NR_ADVANCED_BANDWIDTH_IF_RRC_IDLE_BOOL,
                true);
        sendCarrierConfigChanged();

        // Primary serving NR PCC with cell ID = 1, band = none, bandwidth = 200000
        PhysicalChannelConfig pcc1 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(1)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .setCellBandwidthDownlinkKhz(19999)
                .build();
        // Secondary serving NR PCC with cell ID = 2, band = 41, bandwidth = 10000
        PhysicalChannelConfig pcc2 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(2)
                .setCellConnectionStatus(CellInfo.CONNECTION_SECONDARY_SERVING)
                .setCellBandwidthDownlinkKhz(10000)
                .setBand(41)
                .build();
        // Primary serving NR PCC with cell ID = 3, band = 77, bandwidth = 0
        PhysicalChannelConfig pcc3 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(3)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .setBand(77)
                .build();

        List<PhysicalChannelConfig> physicalChannelConfigs = new ArrayList<>();
        physicalChannelConfigs.add(pcc1);
        physicalChannelConfigs.add(pcc2);
        doReturn(physicalChannelConfigs).when(mSST).getPhysicalChannelConfigList();

        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());

        // bands and bandwidths should stay ratcheted even if an empty PCC list is sent
        doReturn(new ArrayList<>()).when(mSST).getPhysicalChannelConfigList();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, new ArrayList<>(), null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());

        // bands and bandwidths should stay ratcheted as long as anchor NR cell is the same
        physicalChannelConfigs.remove(pcc2);
        doReturn(physicalChannelConfigs).when(mSST).getPhysicalChannelConfigList();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());

        // bands and bandwidths should no longer be ratcheted if anchor NR cell changes
        // add pcc3 to front of list to ensure anchor NR cell changes from 1 -> 3
        physicalChannelConfigs.add(0, pcc3);
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());

        physicalChannelConfigs.add(pcc2);
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelConfigChangedWithoutRatcheting() throws Exception {
        testTransitionToCurrentStateNrConnected();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41, 77});
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        sendCarrierConfigChanged();

        // Primary serving NR PCC with cell ID = 1, band = none, bandwidth = 200000
        PhysicalChannelConfig pcc1 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(1)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .setCellBandwidthDownlinkKhz(19999)
                .build();
        // Secondary serving NR PCC with cell ID = 2, band = 41, bandwidth = 10000
        PhysicalChannelConfig pcc2 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(2)
                .setCellConnectionStatus(CellInfo.CONNECTION_SECONDARY_SERVING)
                .setCellBandwidthDownlinkKhz(10000)
                .setBand(41)
                .build();

        List<PhysicalChannelConfig> physicalChannelConfigs = new ArrayList<>();
        physicalChannelConfigs.add(pcc1);
        physicalChannelConfigs.add(pcc2);
        doReturn(physicalChannelConfigs).when(mSST).getPhysicalChannelConfigList();

        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());

        // bands and bandwidths should stay ratcheted even if an empty PCC list is sent
        doReturn(new ArrayList<>()).when(mSST).getPhysicalChannelConfigList();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, new ArrayList<>(), null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());

        // bands and bandwidths should change if PCC list changes
        physicalChannelConfigs.remove(pcc2);
        doReturn(physicalChannelConfigs).when(mSST).getPhysicalChannelConfigList();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelConfigChangedUsingUserDataForRrc() throws Exception {
        testTransitionToCurrentStateNrConnected();
        mBundle.putIntArray(CarrierConfigManager.KEY_ADDITIONAL_NR_ADVANCED_BANDS_INT_ARRAY,
                new int[]{41, 77});
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        mBundle.putBoolean(CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL,
                true);
        sendCarrierConfigChanged();

        // Primary serving NR PCC with cell ID = 1, band = none, bandwidth = 200000
        PhysicalChannelConfig pcc1 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(1)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .setCellBandwidthDownlinkKhz(19999)
                .build();
        // Secondary serving NR PCC with cell ID = 2, band = 41, bandwidth = 10000
        PhysicalChannelConfig pcc2 = new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setPhysicalCellId(2)
                .setCellConnectionStatus(CellInfo.CONNECTION_SECONDARY_SERVING)
                .setCellBandwidthDownlinkKhz(10000)
                .setBand(41)
                .build();

        List<PhysicalChannelConfig> physicalChannelConfigs = new ArrayList<>();
        physicalChannelConfigs.add(pcc1);
        physicalChannelConfigs.add(pcc2);
        doReturn(physicalChannelConfigs).when(mSST).getPhysicalChannelConfigList();

        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());

        // bands and bandwidths should not stay the same even if an empty PCC list is sent
        doReturn(new ArrayList<>()).when(mSST).getPhysicalChannelConfigList();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, new ArrayList<>(), null));
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());

        // bands and bandwidths should change if PCC list changes
        doReturn(physicalChannelConfigs).when(mSST).getPhysicalChannelConfigList();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null, physicalChannelConfigs, null));
        processAllMessages();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testNrPhysicalChannelChangeFromNrConnectedMmwaveToLteConnected() throws Exception {
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();

        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        setPhysicalLinkStatus(true);
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);

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
        sendCarrierConfigChanged();
        processAllMessages();
        testTransitionToCurrentStateNrConnectedMmwave();
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);

        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelChangeFromLteToLteCaInLegacyState() throws Exception {
        testTransitionToCurrentStateLegacy();
        updateOverrideNetworkType();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {30000}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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
        sendCarrierConfigChanged();

        // Transition to LTE connected state
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE -> LTE+
        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {30000}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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
        sendCarrierConfigChanged();

        // Transition to idle state
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());

        // LTE -> LTE+
        doReturn(true).when(mServiceState).isUsingCarrierAggregation();
        doReturn(new int[] {30000}).when(mServiceState).getCellBandwidths();
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testEventPhysicalLinkStatusChanged() throws Exception {
        testTransitionToCurrentStateLteConnected();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
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
        mNetworkTypeController.sendMessage(11 /* EVENT_PHYSICAL_CHANNEL_CONFIGS_CHANGED */,
                new AsyncResult(null,
                        mPhone.getServiceStateTracker().getPhysicalChannelConfigList(), null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
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
        sendCarrierConfigChanged();
        processAllMessages();
        testTransitionToCurrentStateLteConnected_usingUserDataForRrcDetection();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_DORMANT, null));

        processAllMessages();
        assertEquals("not_restricted_rrc_idle", getCurrentState().getName());
    }

    @Test
    public void testEventPhysicalChannelConfigNotifChanged() throws Exception {
        testTransitionToCurrentStateNrConnected();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        mNetworkTypeController.sendMessage(5 /* EVENT_PHYSICAL_CHANNEL_CONFIG_NOTIF_CHANGED */,
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
        mNetworkTypeController.sendMessage(9 /* EVENT_RADIO_OFF_OR_UNAVAILABLE */);
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

        mNetworkTypeController.sendMessage(10 /* EVENT_PREFERRED_NETWORK_MODE_CHANGED */);
        processAllMessages();
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
    }

    @Test
    public void testPrimaryTimerExpire() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        sendCarrierConfigChanged();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testPrimaryTimerDeviceIdleMode() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        sendCarrierConfigChanged();

        IPowerManager powerManager = mock(IPowerManager.class);
        PowerManager pm = new PowerManager(mContext, powerManager, mock(IThermalService.class),
                new Handler(Looper.myLooper()));
        doReturn(pm).when(mContext).getSystemService(Context.POWER_SERVICE);
        doReturn(true).when(powerManager).isDeviceIdleMode();
        mNetworkTypeController.sendMessage(12 /* EVENT_DEVICE_IDLE_MODE_CHANGED */);

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should not trigger timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testPrimaryTimerReset() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        sendCarrierConfigChanged();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // trigger 10 second timer after disconnecting from NR
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // reconnect to NR in the middle of the timer
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testPrimaryTimerReset_theNetworkModeWithoutNr() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        sendCarrierConfigChanged();

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
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        // timer should be reset.
        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testPrimaryTimerExpireMmwave() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        sendCarrierConfigChanged();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testPrimaryTimerResetMmwave() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        sendCarrierConfigChanged();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // trigger 10 second timer after disconnecting from NR
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // reconnect to NR in the middle of the timer
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);

        // timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testSecondaryTimerExpire() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected,any,30");
        sendCarrierConfigChanged();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testSecondaryTimerReset() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected,any,30");
        sendCarrierConfigChanged();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // reconnect to NR in the middle of the timer
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testSecondaryTimerExpireMmwave() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        sendCarrierConfigChanged();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testSecondaryTimerResetMmwave() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        sendCarrierConfigChanged();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // primary timer expires
        moveTimeForward(10 * 1000);
        processAllMessages();

        // should trigger 30 second secondary timer
        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // reconnect to NR in the middle of the timer
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);

        // secondary timer expires
        moveTimeForward(30 * 1000);
        processAllMessages();

        // timer should not have gone off
        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testNrTimerResetIn3g() throws Exception {
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        sendCarrierConfigChanged();

        assertEquals("connected_mmwave", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(ServiceState.FREQUENCY_RANGE_LOW).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // rat is UMTS, should stop timer
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(nri).when(mServiceState).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testNrTimerResetWhenConnected() throws Exception {
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        sendCarrierConfigChanged();

        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(4 /* EVENT_PHYSICAL_LINK_STATUS_CHANGED */,
                new AsyncResult(null, DataCallResponse.LINK_STATUS_ACTIVE, null));
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("not_restricted_rrc_con", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());

        // should trigger 10 second primary timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_UNKNOWN).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // rat is NR, should stop timer
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        doReturn(nri).when(mServiceState).getNetworkRegistrationInfo(anyInt(), anyInt());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                mNetworkTypeController.getOverrideNetworkType());
        assertFalse(mNetworkTypeController.areAnyTimersActive());
    }

    @Test
    public void testNrTimerResetWhenConnectedAdvanced() throws Exception {
        testTransitionToCurrentStateNrConnectedMmwave();
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,10;connected,any,10;not_restricted_rrc_con,any,10");
        mBundle.putString(CarrierConfigManager.KEY_5G_ICON_DISPLAY_SECONDARY_GRACE_PERIOD_STRING,
                "connected_mmwave,any,30");
        sendCarrierConfigChanged();

        // should trigger 10 second primary timer
        doReturn(NetworkRegistrationInfo.NR_STATE_NONE).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_UNKNOWN).when(mServiceState).getNrFrequencyRange();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("legacy", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());

        // not advanced, should not stop timer
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();

        assertEquals("connected", getCurrentState().getName());
        assertEquals(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                mNetworkTypeController.getOverrideNetworkType());
        assertTrue(mNetworkTypeController.areAnyTimersActive());
    }

    private void setPhysicalLinkStatus(boolean state) {
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        // If PhysicalChannelConfigList is empty, PhysicalLinkStatus is
        // DataCallResponse.LINK_STATUS_DORMANT
        // If PhysicalChannelConfigList is not empty, PhysicalLinkStatus is
        // DataCallResponse.LINK_STATUS_ACTIVE

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
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        doReturn(new int[] {19999}).when(mServiceState).getCellBandwidths();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        sendCarrierConfigChanged();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }

    @Test
    public void testTransitionToCurrentStateNrConnectedWithHighBandwidth() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        List<PhysicalChannelConfig> lastPhysicalChannelConfigList = new ArrayList<>();
        lastPhysicalChannelConfigList.add(new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_NR)
                .setCellConnectionStatus(CellInfo.CONNECTION_PRIMARY_SERVING)
                .setPhysicalCellId(1)
                .setCellBandwidthDownlinkKhz(19999)
                .build());
        lastPhysicalChannelConfigList.add(new PhysicalChannelConfig.Builder()
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCellBandwidthDownlinkKhz(10000)
                .build());
        doReturn(lastPhysicalChannelConfigList).when(mSST).getPhysicalChannelConfigList();
        mBundle.putInt(CarrierConfigManager.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ_INT, 20000);
        sendCarrierConfigChanged();
        assertEquals("connected", getCurrentState().getName());

        mBundle.putBoolean(
                CarrierConfigManager.KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH_BOOL,
                true);
        sendCarrierConfigChanged();
        assertEquals("connected_mmwave", getCurrentState().getName());
    }

    @Test
    public void testNrAdvancedDisabledWhileRoaming() throws Exception {
        assertEquals("DefaultState", getCurrentState().getName());
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        doReturn(ServiceState.FREQUENCY_RANGE_MMWAVE).when(mServiceState).getNrFrequencyRange();
        mBundle.putBoolean(CarrierConfigManager.KEY_ENABLE_NR_ADVANCED_WHILE_ROAMING_BOOL, false);
        sendCarrierConfigChanged();

        mNetworkTypeController.sendMessage(3 /* EVENT_SERVICE_STATE_CHANGED */);
        processAllMessages();
        assertEquals("connected", getCurrentState().getName());
    }
}
