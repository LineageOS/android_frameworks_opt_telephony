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

package com.android.internal.telephony.emergency;

import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WWAN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.EmergencyRegResult;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.data.PhoneSwitcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Unit tests for EmergencyStateTracker
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class EmergencyStateTrackerTest extends TelephonyTest {

    private static final String TEST_CALL_ID = "00001";

    @Mock EmergencyStateTracker.PhoneFactoryProxy mPhoneFactoryProxy;
    @Mock EmergencyStateTracker.PhoneSwitcherProxy mPhoneSwitcherProxy;
    @Mock EmergencyStateTracker.TelephonyManagerProxy mTelephonyManagerProxy;
    @Mock PhoneSwitcher mPhoneSwitcher;
    @Mock RadioOnHelper mRadioOnHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void getInstance_notInitializedTillMake() throws IllegalStateException {
        assertThrows(IllegalStateException.class, () -> {
            EmergencyStateTracker.getInstance();
        });

        EmergencyStateTracker.make(mContext, true);

        assertNotNull(EmergencyStateTracker.getInstance());
    }

    @Test
    @SmallTest
    public void getInstance_returnsSameInstance() {
        EmergencyStateTracker.make(mContext, true);
        EmergencyStateTracker instance1 = EmergencyStateTracker.getInstance();
        EmergencyStateTracker instance2 = EmergencyStateTracker.getInstance();

        assertSame(instance1, instance2);
    }

    /**
     * Test that the EmergencyStateTracker turns on radio, performs a DDS switch and sets emergency
     * mode switch when we are not roaming and the carrier only supports SUPL over the data plane.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_radioOff_turnOnRadioSwitchDdsAndSetEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones and set radio off
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                false /* isRadioOn */);
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                null);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY);
        cfgManager.getConfigForSubId(testPhone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "150");
        // Spy is used to capture consumer in delayDialForDdsSwitch
        EmergencyStateTracker spyEst = spy(emergencyStateTracker);

        CompletableFuture<Integer> unused = spyEst.startEmergencyCall(testPhone, TEST_CALL_ID,
                false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false));
        // isOkToCall() should return true once radio is on
        assertFalse(callback.getValue().isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE));
        when(mSST.isRadioOn()).thenReturn(true);
        assertTrue(callback.getValue().isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE));
        // Once radio on is complete, trigger delay dial
        callback.getValue().onComplete(null, true);
        ArgumentCaptor<Consumer<Boolean>> completeConsumer = ArgumentCaptor
                .forClass(Consumer.class);
        verify(spyEst).delayDialForDdsSwitch(eq(testPhone), completeConsumer.capture());
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(0) /* phoneId */ ,
                eq(150) /* extensionTime */, any());
        // After dds switch completes successfully, set emergency mode
        completeConsumer.getValue().accept(true);
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    /**
     * Test that if startEmergencyCall fails to turn on radio, then it's future completes with
     * DisconnectCause.POWER_OFF.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_radioOnFails_returnsDisconnectCausePowerOff() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones and set radio off
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                false /* isRadioOn */);

        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false));
        // Verify future completes with DisconnectCause.POWER_OFF if radio not ready
        CompletableFuture<Void> unused = future.thenAccept((result) -> {
            assertEquals((Integer) result, (Integer) DisconnectCause.POWER_OFF);
        });
        callback.getValue().onComplete(null, false /* isRadioReady */);
    }

    /**
     * Test that the EmergencyStateTracker does not perform a DDS switch when the carrier supports
     * control-plane fallback. Radio is set to on so RadioOnHelper not triggered.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_cpFallback_noDdsSwitch() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones and set radio on
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                true /* isRadioOn */);
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                null);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK);
        cfgManager.getConfigForSubId(testPhone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        // Radio already on so shouldn't trigger this
        verify(mRadioOnHelper, never()).triggerRadioOnAndListen(any(), anyBoolean(), any(),
                anyBoolean());
        // Carrier supports control-plane fallback, so no DDS switch
        verify(mPhoneSwitcher, never()).overrideDefaultDataForEmergency(anyInt(), anyInt(), any());
    }

    /**
     * Test that the EmergencyStateTracker does not perform a DDS switch if the non-DDS supports
     * SUPL.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_supportsSuplOnNonDds_noDdsSwitch() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                false /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                true /* isRadioOn */);
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                null);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY);
        cfgManager.getConfigForSubId(testPhone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        // non-DDS supports SUPL, so no DDS switch
        verify(mPhoneSwitcher, never()).overrideDefaultDataForEmergency(anyInt(), anyInt(), any());
    }

    /**
     * Test that the EmergencyStateTracker does not perform a DDS switch when the carrier does not
     * support control-plane fallback while roaming.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_roaming_noDdsSwitch() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(true /* isRoaming */,
                true /* isRadioOn */);
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                null);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY);
        cfgManager.getConfigForSubId(testPhone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        // Is roaming, so no DDS switch
        verify(mPhoneSwitcher, never()).overrideDefaultDataForEmergency(anyInt(), anyInt(), any());
    }

    /**
     * Test that the EmergencyStateTracker does perform a DDS switch even though the carrier
     * supports control-plane fallback and the roaming partner is configured to look like a home
     * network.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_roamingCarrierConfig_switchDds() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                true /* isRadioOn */);
        // Setup voice roaming scenario
        String testRoamingOperator = "001001";
        testPhone.getServiceState().setOperatorName("TestTel", "TestTel", testRoamingOperator);
        String[] roamingPlmns = new String[1];
        roamingPlmns[0] = testRoamingOperator;
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                roamingPlmns);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK);
        cfgManager.getConfigForSubId(testPhone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        // Verify DDS switch
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(0) /* phoneId */,
                eq(0) /* extensionTime */, any());
    }

    /**
     * Test that the EmergencyStateTracker does perform a DDS switch even though the carrier
     * supports control-plane fallback if we are roaming and the roaming partner is configured to
     * use data plane only SUPL.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_roamingCarrierConfigWhileRoaming_switchDds() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(true /* isRoaming */,
                true /* isRadioOn */);
        // Setup voice roaming scenario
        String testRoamingOperator = "001001";
        testPhone.getServiceState().setOperatorName("TestTel", "TestTel", testRoamingOperator);
        String[] roamingPlmns = new String[1];
        roamingPlmns[0] = testRoamingOperator;
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                roamingPlmns);
        cfgManager.getConfigForSubId(testPhone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK);
        cfgManager.getConfigForSubId(testPhone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        // Verify DDS switch
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(0) /* phoneId */,
                eq(0) /* extensionTime */, any());
    }

    /**
     * Test that once EmergencyStateTracker handler receives set emergency mode done message it sets
     * IsInEmergencyCall to true, sets LastEmergencyRegResult and completes future with
     * DisconnectCause.NOT_DISCONNECTED.
     */
    @Test
    @SmallTest
    public void setEmergencyModeDone_notifiesListenersAndCompletesFuture() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(true /* isRoaming */,
                true /* isRadioOn */);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);
        // Verify future completes with DisconnectCause.NOT_DISCONNECTED
        CompletableFuture<Void> unused = future.thenAccept((result) -> {
            assertEquals((Integer) result, (Integer) DisconnectCause.NOT_DISCONNECTED);
        });
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        Handler handler = emergencyStateTracker.getHandler();
        Message msg = new Message();
        EmergencyRegResult regResult = new EmergencyRegResult(0, 0, 0, true, false, 0, 1, "testMcc",
                "testMnc", "testIso");
        AsyncResult ar = new AsyncResult(msg, regResult, null);
        msg.obj = ar;

        msg.what = EmergencyStateTracker.MSG_SET_EMERGENCY_MODE_DONE;
        handler.handleMessage(msg);

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.getEmergencyRegResult().equals(regResult));
    }

    /**
     * Test that once EmergencyStateTracker handler receives message to exit emergency mode, it sets
     * IsInEmergencyCall to false.
     */
    @Test
    @SmallTest
    public void exitEmergencyModeDone_isInEmergencyCallFalse() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(true /* isRoaming */,
                true /* isRadioOn */);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);
        Handler handler = emergencyStateTracker.getHandler();
        Message msg = new Message();
        EmergencyRegResult regResult = new EmergencyRegResult(0, 0, 0, true, false, 0, 1, "testMcc",
                "testMnc", "testIso");
        AsyncResult ar = new AsyncResult(msg, regResult, null);
        msg.obj = ar;
        // Send message to set isInEmergencyCall to true
        msg.what = EmergencyStateTracker.MSG_SET_EMERGENCY_MODE_DONE;
        handler.handleMessage(msg);
        assertTrue(emergencyStateTracker.isInEmergencyCall());

        msg.what = EmergencyStateTracker.MSG_EXIT_EMERGENCY_MODE_DONE;
        handler.handleMessage(msg);

        assertFalse(emergencyStateTracker.isInEmergencyCall());
    }

    /**
     * Test that once EmergencyStateTracker ends call, it exits emergency mode.
     */
    @Test
    @SmallTest
    public void endCall_exitsEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(true /* isRoaming */,
                true /* isRadioOn */);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        emergencyStateTracker.endCall("testId");

        verify(testPhone).exitEmergencyMode(any());
    }

    /**
     * Test that onEmergencyTransportChanged sets the new emergency mode.
     */
    @Test
    @SmallTest
    public void onEmergencyTransportChanged_setsEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(true /* isRoaming */,
                true /* isRadioOn */);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);

        emergencyStateTracker.onEmergencyTransportChanged(MODE_EMERGENCY_WWAN);

        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    private EmergencyStateTracker setupEmergencyStateTracker(
            boolean isSuplDdsSwitchRequiredForEmergencyCall) {
        doReturn(mPhoneSwitcher).when(mPhoneSwitcherProxy).getPhoneSwitcher();
        return new EmergencyStateTracker(mContext, Looper.getMainLooper(),
                isSuplDdsSwitchRequiredForEmergencyCall, mPhoneFactoryProxy, mPhoneSwitcherProxy,
                mTelephonyManagerProxy, mRadioOnHelper);
    }

    private Phone setupTestPhoneForEmergencyCall(boolean isRoaming, boolean isRadioOn) {
        Phone testPhone0 = makeTestPhone(0 /* phoneId */, ServiceState.STATE_IN_SERVICE,
                false /* isEmergencyOnly */);
        Phone testPhone1 = makeTestPhone(1 /* phoneId */, ServiceState.STATE_OUT_OF_SERVICE,
                false /* isEmergencyOnly */);
        List<Phone> phones = new ArrayList<>(2);
        phones.add(testPhone0);
        phones.add(testPhone1);
        doReturn(isRadioOn).when(testPhone0).isRadioOn();
        doReturn(isRadioOn).when(testPhone1).isRadioOn();
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        doReturn(2).when(mTelephonyManagerProxy).getPhoneCount();
        when(mPhoneFactoryProxy.getPhones()).thenReturn(phones.toArray(new Phone[phones.size()]));
        testPhone0.getServiceState().setRoaming(isRoaming);
        return testPhone0;
    }

    private Phone makeTestPhone(int phoneId, int serviceState, boolean isEmergencyOnly) {
        Phone phone = mock(Phone.class);
        ServiceState testServiceState = new ServiceState();
        testServiceState.setState(serviceState);
        testServiceState.setEmergencyOnly(isEmergencyOnly);
        when(phone.getContext()).thenReturn(mContext);
        when(phone.getServiceState()).thenReturn(testServiceState);
        when(phone.getPhoneId()).thenReturn(phoneId);
        when(phone.getSubId()).thenReturn(0);
        when(phone.getServiceStateTracker()).thenReturn(mSST);
        return phone;
    }
}