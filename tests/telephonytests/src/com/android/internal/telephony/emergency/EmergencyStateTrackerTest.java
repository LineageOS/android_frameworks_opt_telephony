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

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS_PS;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;

import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_CALLBACK;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WLAN;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WWAN;
import static com.android.internal.telephony.emergency.EmergencyStateTracker.DEFAULT_WAIT_FOR_IN_SERVICE_TIMEOUT_MS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
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
    private static final String TEST_SMS_ID = "1111";
    private static final String TEST_SMS_ID_2 = "2222";
    private static final int TEST_ECM_EXIT_TIMEOUT_MS = 500;
    private static final EmergencyRegistrationResult E_REG_RESULT = new EmergencyRegistrationResult(
            EUTRAN, REGISTRATION_STATE_HOME, DOMAIN_CS_PS, true, true, 0, 1, "001", "01", "US");

    @Mock EmergencyStateTracker.PhoneFactoryProxy mPhoneFactoryProxy;
    @Mock EmergencyStateTracker.PhoneSwitcherProxy mPhoneSwitcherProxy;
    @Mock EmergencyStateTracker.TelephonyManagerProxy mTelephonyManagerProxy;
    @Mock PhoneSwitcher mPhoneSwitcher;
    @Mock RadioOnHelper mRadioOnHelper;
    @Mock android.telecom.Connection mTestConnection1;
    @Mock android.telecom.Connection mTestConnection2;

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
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "150");
        ServiceState ss = mock(ServiceState.class);
        doReturn(ss).when(mSST).getServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .build();
        doReturn(nri).when(ss).getNetworkRegistrationInfo(anyInt(), anyInt());
        // Spy is used to capture consumer in delayDialForDdsSwitch
        EmergencyStateTracker spyEst = spy(emergencyStateTracker);
        CompletableFuture<Integer> unused = spyEst.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(DEFAULT_WAIT_FOR_IN_SERVICE_TIMEOUT_MS));
        // isOkToCall() should return true when IN_SERVICE state
        assertFalse(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
        when(mSST.isRadioOn()).thenReturn(true);
        assertFalse(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));

        nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(REGISTRATION_STATE_HOME)
                .build();
        doReturn(nri).when(ss).getNetworkRegistrationInfo(anyInt(), anyInt());

        assertTrue(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_IN_SERVICE, false));
        // Once radio on is complete, trigger delay dial
        callback.getValue().onComplete(null, true);
        ArgumentCaptor<Consumer<Boolean>> completeConsumer = ArgumentCaptor
                .forClass(Consumer.class);
        verify(spyEst).switchDdsDelayed(eq(testPhone), completeConsumer.capture());
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(testPhone.getPhoneId()),
                eq(150) /* extensionTime */, any());
        // After dds switch completes successfully, set emergency mode
        completeConsumer.getValue().accept(true);
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    /**
     * Test that the EmergencyStateTracker turns on radio, performs a DDS switch and sets emergency
     * mode switch when we are not roaming and the carrier only supports SUPL over the data plane.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_radioOff_turnOnRadioTimeoutSwitchDdsAndSetEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones and set radio off
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                false /* isRadioOn */);
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "150");
        ServiceState ss = mock(ServiceState.class);
        doReturn(ss).when(mSST).getServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .build();
        doReturn(nri).when(ss).getNetworkRegistrationInfo(anyInt(), anyInt());
        // Spy is used to capture consumer in delayDialForDdsSwitch
        EmergencyStateTracker spyEst = spy(emergencyStateTracker);
        CompletableFuture<Integer> unused = spyEst.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(DEFAULT_WAIT_FOR_IN_SERVICE_TIMEOUT_MS));
        // onTimeout should return true when radion on
        assertFalse(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
        assertFalse(callback.getValue()
                .onTimeout(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
        when(mSST.isRadioOn()).thenReturn(true);

        assertFalse(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
        assertTrue(callback.getValue()
                .onTimeout(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
        // Once radio on is complete, trigger delay dial
        callback.getValue().onComplete(null, true);
        ArgumentCaptor<Consumer<Boolean>> completeConsumer = ArgumentCaptor
                .forClass(Consumer.class);
        verify(spyEst).switchDdsDelayed(eq(testPhone), completeConsumer.capture());
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(testPhone.getPhoneId()),
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
                mTestConnection1, false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(DEFAULT_WAIT_FOR_IN_SERVICE_TIMEOUT_MS));
        // Verify future completes with DisconnectCause.POWER_OFF if radio not ready
        CompletableFuture<Void> unused = future.thenAccept((result) -> {
            assertEquals((Integer) result, (Integer) DisconnectCause.POWER_OFF);
        });
        callback.getValue().onComplete(null, false /* isRadioReady */);
    }

    /**
     * Test that the EmergencyStateTracker turns off satellite modem, performs a DDS switch and
     * sets emergency mode switch when we are not roaming and the carrier only supports SUPL over
     * the data plane.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_satelliteEnabled_turnOnRadioSwitchDdsAndSetEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones and set radio on
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                true /* isRadioOn */);
        when(mSST.isRadioOn()).thenReturn(true);
        // Satellite enabled
        when(mSatelliteController.isSatelliteEnabled()).thenReturn(true);

        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "150");
        // Spy is used to capture consumer in delayDialForDdsSwitch
        EmergencyStateTracker spyEst = spy(emergencyStateTracker);
        CompletableFuture<Integer> unused = spyEst.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(0));
        // isOkToCall() should return true once satellite modem is off
        assertFalse(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_IN_SERVICE, false));
        when(mSatelliteController.isSatelliteEnabled()).thenReturn(false);
        assertTrue(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_IN_SERVICE, false));
        // Once radio on is complete, trigger delay dial
        callback.getValue().onComplete(null, true);
        ArgumentCaptor<Consumer<Boolean>> completeConsumer = ArgumentCaptor
                .forClass(Consumer.class);
        verify(spyEst).switchDdsDelayed(eq(testPhone), completeConsumer.capture());
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(testPhone.getPhoneId()),
                eq(150) /* extensionTime */, any());
        // After dds switch completes successfully, set emergency mode
        completeConsumer.getValue().accept(true);
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    /**
     * Test that if startEmergencyCall fails to turn off satellite modem, then it's future completes
     * with {@link DisconnectCause#SATELLITE_ENABLED}.
     */
    @Test
    @SmallTest
    public void startEmergencyCall_satelliteOffFails_returnsDisconnectCauseSatelliteEnabled() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                true /* isSuplDdsSwitchRequiredForEmergencyCall */);
        // Create test Phones and set radio on
        Phone testPhone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                true /* isRadioOn */);
        // Satellite enabled
        when(mSatelliteController.isSatelliteEnabled()).thenReturn(true);

        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // startEmergencyCall should trigger satellite modem off
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(0));
        // Verify future completes with DisconnectCause.POWER_OFF if radio not ready
        CompletableFuture<Void> unused = future.thenAccept((result) -> {
            assertEquals((Integer) result, (Integer) DisconnectCause.SATELLITE_ENABLED);
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
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // Radio already on so shouldn't trigger this
        verify(mRadioOnHelper, never()).triggerRadioOnAndListen(any(), anyBoolean(), any(),
                anyBoolean(), eq(0));
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
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

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
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

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
        String[] roamingPlmns = new String[] { testRoamingOperator };
        setConfigForDdsSwitch(testPhone, roamingPlmns,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

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
        String[] roamingPlmns = new String[] { testRoamingOperator };
        setConfigForDdsSwitch(testPhone, roamingPlmns,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK, "0");

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // Verify DDS switch
        verify(mPhoneSwitcher).overrideDefaultDataForEmergency(eq(0) /* phoneId */,
                eq(0) /* extensionTime */, any());
    }

    /**
     * Test that once EmergencyStateTracker handler receives set emergency mode done message it sets
     * IsInEmergencyCall to true, sets LastEmergencyRegistrationResult and completes future with
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
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        // Verify future completes with DisconnectCause.NOT_DISCONNECTED
        CompletableFuture<Void> unused = future.thenAccept((result) -> {
            assertEquals((Integer) result, (Integer) DisconnectCause.NOT_DISCONNECTED);
        });
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.getEmergencyRegistrationResult().equals(E_REG_RESULT));
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
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
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertFalse(emergencyStateTracker.isInEmergencyCall());
        verify(testPhone).exitEmergencyMode(any(Message.class));
    }

    /**
     * Test that onEmergencyCallDomainUpdated updates the domain correctly so ECBM PS domain is
     * detected.
     */
    @Test
    @SmallTest
    public void onEmergencyCallDomainUpdated_PsDomain() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        // set domain
        emergencyStateTracker.onEmergencyCallDomainUpdated(PhoneConstants.PHONE_TYPE_IMS,
                mTestConnection1);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);

        // Make sure CS ECBM is true
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
    }

    /**
     * Test that onEmergencyCallDomainUpdated updates the domain correctly so ECBM CS domain is
     * detected.
     */
    @Test
    @SmallTest
    public void onEmergencyCallDomainUpdated_CsDomain() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        // set domain
        emergencyStateTracker.onEmergencyCallDomainUpdated(PhoneConstants.PHONE_TYPE_CDMA,
                mTestConnection1);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);

        // Make sure IMS ECBM is true
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInCdmaEcm());
        assertFalse(emergencyStateTracker.isInImsEcm());
    }

    /**
     * Ensure that if for some reason we enter ECBM for CS domain and the Phone type is GSM,
     * isInCdmaEcm returns false.
     */
    @Test
    @SmallTest
    public void onEmergencyCallDomainUpdated_CsDomain_Gsm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // For some reason the Phone is reporting GSM instead of CDMA.
        doReturn(PhoneConstants.PHONE_TYPE_GSM).when(testPhone).getPhoneType();
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        // set domain
        emergencyStateTracker.onEmergencyCallDomainUpdated(PhoneConstants.PHONE_TYPE_CDMA,
                mTestConnection1);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());
        assertFalse(emergencyStateTracker.isInImsEcm());
    }

    /**
     * Test that onEmergencyTransportChanged sets the new emergency mode.
     */
    @Test
    @SmallTest
    public void onEmergencyTransportChanged_setEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true );
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WWAN);

        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    /**
     * Test that onEmergencyTransportChangedAndWait sets the new emergency mode.
     */
    @Test
    @SmallTest
    public void onEmergencyTransportChangedAndWait_setEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        emergencyStateTracker.onEmergencyTransportChangedAndWait(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WWAN);

        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    /**
     * Test that after endCall() is called, EmergencyStateTracker will enter ECM if the call was
     * ACTIVE and send related intents.
     */
    @Test
    @SmallTest
    public void endCall_callWasActive_enterEcm() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Start emergency call then enter ECM
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, true);

        assertFalse(emergencyStateTracker.isInEcm());

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEcm());
        // Verify intents are sent that ECM is entered
        ArgumentCaptor<Intent> ecmStateIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendStickyBroadcastAsUser(ecmStateIntent.capture(), eq(UserHandle.ALL));
        assertTrue(ecmStateIntent.getValue()
                .getBooleanExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, true));
        // Verify emergency callback mode set on modem
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any());
    }

    /**
     * Test that after endCall() is called, EmergencyStateTracker will not enter ECM if the call was
     * not ACTIVE.
     */
    @Test
    @SmallTest
    public void endCall_callNotActive_noEcm() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Start emergency call then enter ECM
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        // Call does not reach ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.IDLE, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        emergencyStateTracker.endCall(mTestConnection1);

        assertFalse(emergencyStateTracker.isInEcm());
    }

    /**
     * Test that once endCall() is called and we enter ECM, then we exit ECM after the specified
     * timeout.
     */
    @Test
    @SmallTest
    public void endCall_entersEcm_thenExitsEcmAfterTimeout() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEcm());

        processAllFutureMessages();

        // Verify exitEmergencyMode() is called after timeout
        verify(testPhone).exitEmergencyMode(any(Message.class));
        assertFalse(emergencyStateTracker.isInEmergencyMode());
    }

    /**
     * Test that once endCall() is called and we enter ECM, then we exit ECM when turning on
     * airplane mode.
     */
    @Test
    @SmallTest
    public void endCall_entersEcm_thenExitsEcmWhenTurnOnAirplaneMode() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEcm());

        emergencyStateTracker.onCellularRadioPowerOffRequested();

        // Verify exitEmergencyMode() is called.
        verify(testPhone).exitEmergencyMode(any(Message.class));
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyMode());
    }

    /**
     * Test that after exitEmergencyCallbackMode() is called, the correct intents are sent and
     * emergency mode is exited on the modem.
     */
    @Test
    @SmallTest
    public void exitEmergencyCallbackMode_sendsCorrectIntentsAndExitsEmergencyMode() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        // Start emergency call then enter ECM
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        processAllMessages();
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        // verify ecbm states are correct
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        emergencyStateTracker.exitEmergencyCallbackMode();
        processAllFutureMessages();

        // Ensure ECBM states are all correctly false after we exit.
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());
        // Intents sent for ECM: one for entering ECM and another for exiting
        ArgumentCaptor<Intent> ecmStateIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2))
                .sendStickyBroadcastAsUser(ecmStateIntent.capture(), eq(UserHandle.ALL));
        List<Intent> capturedIntents = ecmStateIntent.getAllValues();
        assertTrue(capturedIntents.get(0)
                .getBooleanExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false));
        assertFalse(capturedIntents.get(1)
                .getBooleanExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false));
        // Verify exitEmergencyMode() is called only once
        verify(testPhone).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testOnEmergencyTransportChangedUsingDifferentThread() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            emergencyStateTracker.onEmergencyTransportChanged(
                    EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WWAN);
        });
        processAllMessages();

        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWithTestEmergencyNumber() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, true);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertFalse(future.isDone());
        verify(phone0).setEmergencyMode(anyInt(), any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallDuringActiveCall() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        // First active call
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Second starting call
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection2, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallInEcm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setEcmSupportedConfig(phone0, true);

        // First active call
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEcm());
        verify(phone0, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(phone0, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));

        // Second emergency call started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection2, false);

        assertFalse(future.isDone());
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(phone0, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallUsingDifferenPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);

        // First emergency call
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        // Second emergency call
        Phone phone1 = getPhone(1);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone1,
                mTestConnection2, false);

        // Returns DisconnectCause#ERROR_UNSPECIFIED immediately.
        assertEquals(future.getNow(DisconnectCause.NOT_DISCONNECTED),
                Integer.valueOf(DisconnectCause.ERROR_UNSPECIFIED));
    }

    @Test
    @SmallTest
    public void testEndCallInEcm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setEcmSupportedConfig(phone0, true);

        // First active call
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Second emergency call started.
        future = emergencyStateTracker.startEmergencyCall(phone0, mTestConnection2, false);

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(future.isDone());

        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WLAN);
        emergencyStateTracker.endCall(mTestConnection2);
        processAllMessages();

        // At this time, ECM is still running so still in ECM.
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WLAN), any(Message.class));
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        verify(phone0, never()).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testRecoverNormalInCellularWhenVoWiFiConnected() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phones
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);

        // Set emergency transport
        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WLAN);

        // Set call properties
        emergencyStateTracker.onEmergencyCallPropertiesChanged(
                android.telecom.Connection.PROPERTY_WIFI, mTestConnection1);

        verify(testPhone, times(0)).cancelEmergencyNetworkScan(anyBoolean(), any());

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        verify(testPhone, times(1)).cancelEmergencyNetworkScan(eq(true), any());
    }

    @Test
    @SmallTest
    public void testStartEmergencySms() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        assertTrue(emergencyStateTracker.getEmergencyRegistrationResult().equals(E_REG_RESULT));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testEndSmsAndExitEmergencyMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        assertTrue(emergencyStateTracker.getEmergencyRegistrationResult().equals(E_REG_RESULT));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_CS);

        verify(phone0).exitEmergencyMode(any(Message.class));
        assertFalse(emergencyStateTracker.isInEmergencyMode());
        // CS domain doesn't support SCBM.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testEndSmsAndEnterEmergencySmsCallbackMode() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        assertTrue(emergencyStateTracker.getEmergencyRegistrationResult().equals(E_REG_RESULT));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);

        verify(mCarrierConfigManager).getConfigForSubId(anyInt(),
                eq(CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT));
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testEndSmsWithSmsSentFailureWhileInScbm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID_2, false);
        processAllMessages();

        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        assertTrue(emergencyStateTracker.isInScbm());

        // Set emergency transport
        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_SMS, MODE_EMERGENCY_WWAN);

        // When MO SMS fails while in SCBM.
        emergencyStateTracker.endSms(TEST_SMS_ID_2, false, DOMAIN_PS);

        verify(mCarrierConfigManager).getConfigForSubId(anyInt(),
                eq(CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT));
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testEndSmsWithSmsSentSuccessWhileInScbm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID_2, false);
        processAllMessages();

        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        assertTrue(emergencyStateTracker.isInScbm());

        // Set emergency transport
        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_SMS, MODE_EMERGENCY_WWAN);

        // When MO SMS is successfully sent while in SCBM.
        emergencyStateTracker.endSms(TEST_SMS_ID_2, true, DOMAIN_PS);

        verify(mCarrierConfigManager, times(2)).getConfigForSubId(anyInt(),
                eq(CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT));
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWithTransportChange() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_SMS, MODE_EMERGENCY_WLAN);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.getEmergencyRegistrationResult().equals(E_REG_RESULT));
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WLAN), any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWithTestEmergencyNumber() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, true);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        verify(phone0, never()).setEmergencyMode(anyInt(), any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWhileSmsBeingSent() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID_2, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWhileEmergencyModeBeingSet() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID_2, false);

        // Returns DisconnectCause#ERROR_UNSPECIFIED immediately.
        assertEquals(future.getNow(DisconnectCause.NOT_DISCONNECTED),
                Integer.valueOf(DisconnectCause.ERROR_UNSPECIFIED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWhileEcmInProgress() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is ended and the emergency callback is entered.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        assertFalse(future.isDone());

        // Completes the emergency mode setting - MODE_EMERGENCY_CALLBACK
        processAllMessages();

        verify(phone0, times(2)).setEmergencyMode(anyInt(), any(Message.class));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWhileInScbm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpScbm(phone0, emergencyStateTracker);

        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID_2, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        verify(phone0, never()).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsUsingDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        Phone phone1 = getPhone(1);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone1,
                TEST_SMS_ID_2, false);

        // Returns DisconnectCause#ERROR_UNSPECIFIED immediately.
        assertEquals(future.getNow(DisconnectCause.NOT_DISCONNECTED),
                Integer.valueOf(DisconnectCause.ERROR_UNSPECIFIED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsWhileInScbmOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ false,
                /* isRadioOn= */ true);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();

        Phone phone1 = getPhone(1);
        setUpAsyncResultForSetEmergencyMode(phone1, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone1,
                TEST_SMS_ID_2, false);

        verify(phone0).exitEmergencyMode(any(Message.class));
        // Waits for exiting emergency mode on other phone.
        assertFalse(future.isDone());

        processAllMessages();

        verify(phone1).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        processAllMessages();

        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallActiveAndSmsOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallInProgressAndSmsOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Emergency call is in progress.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), msgCaptor.capture());

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        assertFalse(future.isDone());

        Message msg = msgCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWhileCallActiveAndInScbmOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        // Emergency call is in active.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        // Expect: DisconnectCause#NOT_DISCONNECTED
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is successfully sent and SCBM entered.
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();

        future = emergencyStateTracker.startEmergencyCall(phone0, mTestConnection2, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Ensure that SCBM is exited.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWhileInEcmAndScbmOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        // Expect: DisconnectCause#NOT_DISCONNECTED
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEcm());

        // Emergency SMS is successfully sent and SCBM entered.
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();

        future = emergencyStateTracker.startEmergencyCall(phone0, mTestConnection2, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEcm());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Ensure that SCBM is exited.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWhileScbmInProgressOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Emergency SMS is successfully sent and SCBM entered.
        setUpScbm(phone0, emergencyStateTracker);

        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Emergency call is started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Ensure that SCBM is exited.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWhileInScbmOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        // Emergency SMS is successfully sent and SCBM entered.
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();

        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Emergency call is started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Ensure that SCBM is exited.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallAndEnterScbmAndEndCallOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        assertFalse(emergencyStateTracker.isInScbm());

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);

        assertTrue(emergencyStateTracker.isInScbm());
        assertTrue(emergencyStateTracker.isInEmergencyMode());

        setEcmSupportedConfig(phone0, false);
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.isInScbm());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsActiveAndCallOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is in active.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        // Emergency call is being started.
        future = emergencyStateTracker.startEmergencyCall(phone0, mTestConnection1, false);
        processAllMessages();

        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsInProgressAndCallOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is in progress.
        CompletableFuture<Integer> smsFuture = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        ArgumentCaptor<Message> smsCaptor = ArgumentCaptor.forClass(Message.class);
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), smsCaptor.capture());

        // Emergency call is being started.
        CompletableFuture<Integer> callFuture = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);

        assertFalse(smsFuture.isDone());
        assertFalse(callFuture.isDone());

        // Response message for setEmergencyMode by SMS.
        Message msg = smsCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Exit emergency mode and set the emergency mode again by the call when the exit result
        // is received for obtaining the latest EmergencyRegistrationResult.
        verify(phone0).exitEmergencyMode(any(Message.class));
        ArgumentCaptor<Message> callCaptor = ArgumentCaptor.forClass(Message.class);
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), callCaptor.capture());

        // Response message for setEmergencyMode by call.
        msg = callCaptor.getAllValues().get(1);
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Expect: DisconnectCause#NOT_DISCONNECTED
        assertEquals(smsFuture.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(callFuture.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        assertTrue(emergencyStateTracker.isInEmergencyCall());
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsInProgressAndStartEmergencyCallAndEndSmsOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is in progress.
        CompletableFuture<Integer> smsFuture = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        ArgumentCaptor<Message> smsCaptor = ArgumentCaptor.forClass(Message.class);
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), smsCaptor.capture());

        // Emergency call is being started.
        CompletableFuture<Integer> callFuture = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);

        assertFalse(smsFuture.isDone());
        assertFalse(callFuture.isDone());

        emergencyStateTracker.endSms(TEST_SMS_ID, false, DOMAIN_PS);

        // Response message for setEmergencyMode by SMS.
        Message msg = smsCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Response message for setEmergencyMode by call.
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        processAllMessages();

        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(callFuture.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        assertTrue(emergencyStateTracker.isInEmergencyCall());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallAndSmsOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started using the different phone.
        Phone phone1 = getPhone(1);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone1,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#ERROR_UNSPECIFIED immediately.
        assertEquals(future.getNow(DisconnectCause.NOT_DISCONNECTED),
                Integer.valueOf(DisconnectCause.ERROR_UNSPECIFIED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsActiveAndCallOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is in active.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        // Emergency call is being started using the different phone.
        Phone phone1 = getPhone(1);
        setUpAsyncResultForSetEmergencyMode(phone1, E_REG_RESULT);
        future = emergencyStateTracker.startEmergencyCall(phone1, mTestConnection1, false);
        processAllMessages();

        verify(phone0).exitEmergencyMode(any(Message.class));
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(phone1).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testStartEmergencySmsInProgressAndCallOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is in progress.
        CompletableFuture<Integer> smsFuture = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        ArgumentCaptor<Message> smsCaptor = ArgumentCaptor.forClass(Message.class);
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), smsCaptor.capture());

        // Emergency call is being started using the different phone.
        Phone phone1 = getPhone(1);
        CompletableFuture<Integer> callFuture = emergencyStateTracker.startEmergencyCall(phone1,
                mTestConnection1, false);

        assertFalse(smsFuture.isDone());
        assertFalse(callFuture.isDone());

        // Response message for setEmergencyMode by SMS.
        Message msg = smsCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Exit emergency mode and set the emergency mode again by the call when the exit result
        // is received for obtaining the latest EmergencyRegistrationResult.
        verify(phone0).exitEmergencyMode(any(Message.class));
        ArgumentCaptor<Message> callCaptor = ArgumentCaptor.forClass(Message.class);
        verify(phone1).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), callCaptor.capture());

        // Response message for setEmergencyMode by call.
        msg = callCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Expect: DisconnectCause#OUTGOING_EMERGENCY_CALL_PLACED
        assertEquals(smsFuture.getNow(DisconnectCause.NOT_DISCONNECTED),
                Integer.valueOf(DisconnectCause.OUTGOING_EMERGENCY_CALL_PLACED));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(callFuture.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        assertTrue(emergencyStateTracker.isInEmergencyCall());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWhileScbmInProgressOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is successfully sent and SCBM entered.
        setUpScbm(phone0, emergencyStateTracker);

        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Emergency call is being started using the different phone.
        Phone phone1 = getPhone(1);
        setUpAsyncResultForSetEmergencyMode(phone1, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone1,
                mTestConnection1, false);
        processAllMessages();

        // Exit emergency mode and set the emergency mode again by the call when the exit result
        // is received for obtaining the latest EmergencyRegResult.
        verify(phone0).exitEmergencyMode(any(Message.class));
        verify(phone1).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Ensure that SCBM is exited.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testStartEmergencyCallWhileInScbmOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForExitEmergencyMode(phone0);
        // Emergency SMS is successfully sent and SCBM entered.
        setUpScbm(phone0, emergencyStateTracker);
        processAllMessages();

        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Emergency call is being started using the different phone.
        Phone phone1 = getPhone(1);
        setUpAsyncResultForSetEmergencyMode(phone1, E_REG_RESULT);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone1,
                mTestConnection1, false);
        processAllMessages();

        // Exit emergency mode and set the emergency mode again by the call when the exit result
        // is received for obtaining the latest EmergencyRegResult.
        verify(phone0).exitEmergencyMode(any(Message.class));
        verify(phone1).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        // Ensure that SCBM is exited.
        assertFalse(emergencyStateTracker.isInScbm());
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeCallAndSmsOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, false);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);
        processAllMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeSmsAndCallOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, false);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeCallAndSmsOnSamePhoneWhenEcmSupported() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);
        processAllMessages();

        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // ECM timeout.
        processAllFutureMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeCallAndSmsOnSamePhoneWhenEcmSupportedAndModeChanged() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_SMS, MODE_EMERGENCY_WWAN);
        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);
        processAllMessages();

        // Enter emergency callback mode and emergency mode changed by SMS end.
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.isInScbm());

        // ECM/SCBM timeout.
        processAllFutureMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertFalse(emergencyStateTracker.isInScbm());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeSmsAndCallOnSamePhoneWhenEcmSupported() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // ECM timeout.
        processAllFutureMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeWhenExitingEcmAndScbm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        setScbmTimerValue(phone0, TEST_ECM_EXIT_TIMEOUT_MS + 100);
        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);
        processAllMessages();

        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.isInScbm());

        // ECM timeout.
        moveTimeForward(TEST_ECM_EXIT_TIMEOUT_MS + 50);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.isInScbm());

        // SCBM timeout.
        processAllFutureMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInScbm());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testExitEmergencyModeWhenExitingScbmAndEcm() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(phone0);
        setEcmSupportedConfig(phone0, true);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                mTestConnection1, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(mTestConnection1);

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        setScbmTimerValue(phone0, TEST_ECM_EXIT_TIMEOUT_MS - 100);
        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);
        processAllMessages();

        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.isInScbm());

        // SCBM timeout.
        moveTimeForward(TEST_ECM_EXIT_TIMEOUT_MS - 50);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());
        assertFalse(emergencyStateTracker.isInScbm());

        // ECM timeout.
        processAllFutureMessages();

        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        verify(phone0).exitEmergencyMode(any(Message.class));
    }

    @Test
    @SmallTest
    public void testSaveKeyEmergencyCallbackModeSupportedBool() {
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        Phone phone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                false /* isRadioOn */);
        when(phone.getSubId()).thenReturn(1);
        setEcmSupportedConfig(phone, true);

        EmergencyStateTracker testEst = setupEmergencyStateTracker(
                false /* isSuplDdsSwitchRequiredForEmergencyCall */);

        assertNotNull(testEst.startEmergencyCall(phone, mTestConnection1, false));

        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);

        verify(cfgManager).registerCarrierConfigChangeListener(any(),
                listenerArgumentCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener carrierConfigChangeListener =
                listenerArgumentCaptor.getAllValues().get(0);

        // Verify carrier config for valid subscription
        assertTrue(testEst.isEmergencyCallbackModeSupported(phone));

        // SIM removed
        when(phone.getSubId()).thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setEcmSupportedConfig(phone, false);

        // Verify default config for invalid subscription
        assertFalse(testEst.isEmergencyCallbackModeSupported(phone));

        // Insert SIM again
        when(phone.getSubId()).thenReturn(1);
        setEcmSupportedConfig(phone, true);

        // onCarrierConfigChanged with valid subscription
        carrierConfigChangeListener.onCarrierConfigChanged(
                phone.getPhoneId(), phone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);

        // SIM removed again
        when(phone.getSubId()).thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setEcmSupportedConfig(phone, false);

        // onCarrierConfigChanged with invalid subscription
        carrierConfigChangeListener.onCarrierConfigChanged(
                phone.getPhoneId(), phone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);

        // Verify saved config for valid subscription
        assertTrue(testEst.isEmergencyCallbackModeSupported(phone));

        // Insert SIM again, but emergency callback mode not supported
        when(phone.getSubId()).thenReturn(1);
        setEcmSupportedConfig(phone, false);

        // onCarrierConfigChanged with valid subscription
        carrierConfigChangeListener.onCarrierConfigChanged(
                phone.getPhoneId(), phone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);

        // Verify carrier config for valid subscription
        assertFalse(testEst.isEmergencyCallbackModeSupported(phone));

        // SIM removed again
        when(phone.getSubId()).thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setEcmSupportedConfig(phone, false);

        // onCarrierConfigChanged with invalid subscription
        carrierConfigChangeListener.onCarrierConfigChanged(
                phone.getPhoneId(), phone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);

        // Verify saved config for valid subscription
        assertFalse(testEst.isEmergencyCallbackModeSupported(phone));
    }

    /**
     * Test that new emergency call is dialed while in emergency callback mode and completes.
     */
    @Test
    @SmallTest
    public void exitEmergencyCallbackMode_NewEmergencyCallDialedAndCompletes() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        GsmCdmaPhone testPhone = (GsmCdmaPhone) setupTestPhoneForEmergencyCall(
                /* isRoaming= */ true, /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        // Start emergency call then enter ECM
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        processAllMessages();

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        // verify ecbm states are correct
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // 2nd call while in emergency callback mode
        unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WWAN);
        processAllMessages();
        processAllFutureMessages();

        // verify ecbm states are not changed.
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // Verify exitEmergencyMode() is not called.
        verify(testPhone, never()).exitEmergencyMode(any(Message.class));

        // Verify ECBM timer cancel.
        verify(testPhone).notifyEcbmTimerReset(eq(Boolean.TRUE));

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, mTestConnection1);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        // verify ecbm states are correct
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // Verify ECBM timer reset.
        verify(testPhone).notifyEcbmTimerReset(eq(Boolean.FALSE));

        // Verify exitEmergencyMode() is not called.
        verify(testPhone, never()).exitEmergencyMode(any(Message.class));

        processAllFutureMessages();

        // Ensure ECBM states are all correctly false after we exit.
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // Verify exitEmergencyMode() is called.
        verify(testPhone).exitEmergencyMode(any(Message.class));
    }

    /**
     * Test that new emergency call is dialed while in emergency callback mode and it fails.
     */
    @Test
    @SmallTest
    public void exitEmergencyCallbackMode_NewEmergencyCallDialedAndFails() {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        GsmCdmaPhone testPhone = (GsmCdmaPhone) setupTestPhoneForEmergencyCall(
                /* isRoaming= */ true, /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        // Start emergency call then enter ECM
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        processAllMessages();

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, mTestConnection1);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, mTestConnection1);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);
        // End call to enter ECM
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        // verify ecbm states are correct
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // 2nd call while in emergency callback mode
        unused = emergencyStateTracker.startEmergencyCall(testPhone,
                mTestConnection1, false);
        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WWAN);
        processAllMessages();
        processAllFutureMessages();

        // verify ecbm states are not changed.
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // Verify exitEmergencyMode() is not called.
        verify(testPhone, never()).exitEmergencyMode(any(Message.class));

        // Verify ECBM timer cancel.
        verify(testPhone).notifyEcbmTimerReset(eq(Boolean.TRUE));

        // End call to return to ECM
        emergencyStateTracker.endCall(mTestConnection1);
        processAllMessages();

        // verify ecbm states are correct
        assertTrue(emergencyStateTracker.isInEcm());
        assertTrue(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // Verify ECBM timer reset.
        verify(testPhone).notifyEcbmTimerReset(eq(Boolean.FALSE));

        // Verify exitEmergencyMode() is not called.
        verify(testPhone, never()).exitEmergencyMode(any(Message.class));

        processAllFutureMessages();

        // Ensure ECBM states are all correctly false after we exit.
        assertFalse(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInImsEcm());
        assertFalse(emergencyStateTracker.isInCdmaEcm());

        // Verify exitEmergencyMode() is called.
        verify(testPhone).exitEmergencyMode(any(Message.class));
    }

    private EmergencyStateTracker setupEmergencyStateTracker(
            boolean isSuplDdsSwitchRequiredForEmergencyCall) {
        doReturn(mPhoneSwitcher).when(mPhoneSwitcherProxy).getPhoneSwitcher();
        doNothing().when(mPhoneSwitcher).overrideDefaultDataForEmergency(
                anyInt(), anyInt(), any());
        return new EmergencyStateTracker(mContext, mTestableLooper.getLooper(),
                isSuplDdsSwitchRequiredForEmergencyCall, mPhoneFactoryProxy, mPhoneSwitcherProxy,
                mTelephonyManagerProxy, mRadioOnHelper, TEST_ECM_EXIT_TIMEOUT_MS);
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

    private Phone getPhone(int phoneId) {
        Phone[] phones = mPhoneFactoryProxy.getPhones();
        return phones[phoneId];
    }

    private Phone makeTestPhone(int phoneId, int serviceState, boolean isEmergencyOnly) {
        GsmCdmaPhone phone = mock(GsmCdmaPhone.class);
        ServiceState testServiceState = new ServiceState();
        testServiceState.setState(serviceState);
        testServiceState.setEmergencyOnly(isEmergencyOnly);
        when(phone.getContext()).thenReturn(mContext);
        when(phone.getServiceState()).thenReturn(testServiceState);
        when(phone.getPhoneId()).thenReturn(phoneId);
        when(phone.getSubId()).thenReturn(0);
        when(phone.getServiceStateTracker()).thenReturn(mSST);
        when(phone.getUnitTestMode()).thenReturn(true);
        // Initialize the phone as a CDMA phone for now for ease of testing ECBM.
        // Tests can individually override this to GSM if required for the test.
        doReturn(PhoneConstants.PHONE_TYPE_CDMA).when(phone).getPhoneType();
        doNothing().when(phone).notifyEmergencyCallRegistrants(anyBoolean());
        return phone;
    }

    private void setEcmSupportedConfig(Phone phone, boolean ecmSupported) {
        mCarrierConfigManager.getConfigForSubId(phone.getSubId()).putBoolean(
                CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALLBACK_MODE_SUPPORTED_BOOL,
                ecmSupported);
    }

    private void setConfigForDdsSwitch(Phone phone, String[] roaminPlmns,
            int suplEmergencyModeType, String esExtensionSec) {
        mCarrierConfigManager.getConfigForSubId(phone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                roaminPlmns);
        mCarrierConfigManager.getConfigForSubId(phone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                suplEmergencyModeType);
        mCarrierConfigManager.getConfigForSubId(phone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, esExtensionSec);
    }

    private void setUpAsyncResultForSetEmergencyMode(Phone phone,
            EmergencyRegistrationResult regResult) {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final Message msg = (Message) args[1];
            AsyncResult.forMessage(msg, regResult, null);
            msg.sendToTarget();
            return null;
        }).when(phone).setEmergencyMode(anyInt(), any(Message.class));
    }

    private void setUpAsyncResultForExitEmergencyMode(Phone phone) {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final Message msg = (Message) args[0];
            AsyncResult.forMessage(msg, null, null);
            msg.sendToTarget();
            return null;
        }).when(phone).exitEmergencyMode(any(Message.class));
    }

    private void setUpScbm(Phone phone, EmergencyStateTracker emergencyStateTracker) {
        setUpAsyncResultForSetEmergencyMode(phone, E_REG_RESULT);
        setEcmSupportedConfig(phone, true);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone,
                TEST_SMS_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        verify(phone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        assertTrue(emergencyStateTracker.getEmergencyRegistrationResult().equals(E_REG_RESULT));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        // Expect: entering SCBM.
        emergencyStateTracker.endSms(TEST_SMS_ID, true, DOMAIN_PS);

        verify(mCarrierConfigManager).getConfigForSubId(anyInt(),
                eq(CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT));
        if (!emergencyStateTracker.isInEcm() && !emergencyStateTracker.isInEmergencyCall()) {
            verify(phone).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
        }
        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInScbm());
    }

    private void setScbmTimerValue(Phone phone, int millis) {
        mCarrierConfigManager.getConfigForSubId(phone.getSubId()).putInt(
                CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT, millis);
    }
}
