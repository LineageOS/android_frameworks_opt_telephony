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
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS_PS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;

import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_CALLBACK;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WLAN;
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
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.EmergencyRegResult;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

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
    private static final String TEST_CALL_ID = "TC@TEST1";
    private static final String TEST_CALL_ID_2 = "TC@TEST2";
    private static final String TEST_SMS_ID = "1111";
    private static final String TEST_SMS_ID_2 = "2222";
    private static final long TEST_ECM_EXIT_TIMEOUT_MS = 500;
    private static final EmergencyRegResult E_REG_RESULT = new EmergencyRegResult(
            EUTRAN, REGISTRATION_STATE_HOME, DOMAIN_CS_PS, true, true, 0, 1, "001", "01", "US");

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
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "150");
        // Spy is used to capture consumer in delayDialForDdsSwitch
        EmergencyStateTracker spyEst = spy(emergencyStateTracker);
        CompletableFuture<Integer> unused = spyEst.startEmergencyCall(testPhone, TEST_CALL_ID,
                false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(0));
        // isOkToCall() should return true once radio is on
        assertFalse(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
        when(mSST.isRadioOn()).thenReturn(true);
        assertTrue(callback.getValue()
                .isOkToCall(testPhone, ServiceState.STATE_OUT_OF_SERVICE, false));
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
                TEST_CALL_ID, false);

        // startEmergencyCall should trigger radio on
        ArgumentCaptor<RadioOnStateListener.Callback> callback = ArgumentCaptor
                .forClass(RadioOnStateListener.Callback.class);
        verify(mRadioOnHelper).triggerRadioOnAndListen(callback.capture(), eq(true), eq(testPhone),
                eq(false), eq(0));
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
        CompletableFuture<Integer> unused = spyEst.startEmergencyCall(testPhone, TEST_CALL_ID,
                false);

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
                TEST_CALL_ID, false);

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
                TEST_CALL_ID, false);

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
        setConfigForDdsSwitch(testPhone, null,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY, "0");

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
        String[] roamingPlmns = new String[] { testRoamingOperator };
        setConfigForDdsSwitch(testPhone, roamingPlmns,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK, "0");

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
        String[] roamingPlmns = new String[] { testRoamingOperator };
        setConfigForDdsSwitch(testPhone, roamingPlmns,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_FALLBACK, "0");

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
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        // Call startEmergencyCall() to set testPhone
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);
        // Verify future completes with DisconnectCause.NOT_DISCONNECTED
        CompletableFuture<Void> unused = future.thenAccept((result) -> {
            assertEquals((Integer) result, (Integer) DisconnectCause.NOT_DISCONNECTED);
        });
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        assertTrue(emergencyStateTracker.getEmergencyRegResult().equals(E_REG_RESULT));
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(testPhone).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.endCall(TEST_CALL_ID);
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
                TEST_CALL_ID, false);

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        // set domain
        emergencyStateTracker.onEmergencyCallDomainUpdated(PhoneConstants.PHONE_TYPE_IMS,
                TEST_CALL_ID);
        // End call to enter ECM
        emergencyStateTracker.endCall(TEST_CALL_ID);

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
                TEST_CALL_ID, false);

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        // set domain
        emergencyStateTracker.onEmergencyCallDomainUpdated(PhoneConstants.PHONE_TYPE_CDMA,
                TEST_CALL_ID);
        // End call to enter ECM
        emergencyStateTracker.endCall(TEST_CALL_ID);

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
                TEST_CALL_ID, false);

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        // set domain
        emergencyStateTracker.onEmergencyCallDomainUpdated(PhoneConstants.PHONE_TYPE_CDMA,
                TEST_CALL_ID);
        // End call to enter ECM
        emergencyStateTracker.endCall(TEST_CALL_ID);

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
                TEST_CALL_ID, false);

        emergencyStateTracker.onEmergencyTransportChanged(
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
                TEST_CALL_ID, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, true);

        assertFalse(emergencyStateTracker.isInEcm());

        emergencyStateTracker.endCall(TEST_CALL_ID);

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
                TEST_CALL_ID, false);
        // Call does not reach ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.IDLE, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        emergencyStateTracker.endCall(TEST_CALL_ID);

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
                TEST_CALL_ID, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(TEST_CALL_ID);

        assertTrue(emergencyStateTracker.isInEcm());

        processAllFutureMessages();

        // Verify exitEmergencyMode() is called after timeout
        verify(testPhone).exitEmergencyMode(any(Message.class));
        assertFalse(emergencyStateTracker.isInEmergencyMode());
    }

    /**
     * Test that once endCall() for IMS call is called and we enter ECM, then we exit ECM
     * after the specified timeout.
     */
    @Test
    @SmallTest
    public void endCall_entersEcm_thenExitsEcmAfterTimeoutImsCall() throws Exception {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(TEST_CALL_ID);

        assertTrue(emergencyStateTracker.isInEcm());

        Context mockContext = mock(Context.class);
        replaceInstance(EmergencyStateTracker.class, "mContext",
                emergencyStateTracker, mockContext);
        processAllFutureMessages();

        ArgumentCaptor<TelephonyCallback> callbackCaptor =
                ArgumentCaptor.forClass(TelephonyCallback.class);

        verify(mTelephonyManagerProxy).registerTelephonyCallback(eq(testPhone.getSubId()),
                  any(), callbackCaptor.capture());

        TelephonyCallback callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // Verify exitEmergencyMode() is called after timeout
        verify(testPhone).exitEmergencyMode(any(Message.class));
        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        verify(mTelephonyManagerProxy).unregisterTelephonyCallback(eq(callback));
    }

    /**
     * Test that startEmergencyCall() is called right after exiting ECM on the same slot.
     */
    @Test
    @SmallTest
    public void exitEcm_thenDialEmergencyCallOnTheSameSlotRightAfter() throws Exception {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        emergencyStateTracker.setPdnDisconnectionTimeoutMs(0);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);

        verify(testPhone, times(0)).setEmergencyMode(anyInt(), any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);
        processAllMessages();

        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(TEST_CALL_ID);

        assertTrue(emergencyStateTracker.isInEcm());

        Context mockContext = mock(Context.class);
        replaceInstance(EmergencyStateTracker.class, "mContext",
                emergencyStateTracker, mockContext);
        processAllFutureMessages();

        ArgumentCaptor<TelephonyCallback> callbackCaptor =
                ArgumentCaptor.forClass(TelephonyCallback.class);

        verify(mTelephonyManagerProxy).registerTelephonyCallback(eq(testPhone.getSubId()),
                  any(), callbackCaptor.capture());

        TelephonyCallback callback = callbackCaptor.getValue();

        assertNotNull(callback);
        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        verify(mTelephonyManagerProxy, times(0)).unregisterTelephonyCallback(eq(callback));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK),
                any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));

        replaceInstance(EmergencyStateTracker.class, "mContext", emergencyStateTracker, mContext);

        // dial on the same slot
        unused = emergencyStateTracker.startEmergencyCall(testPhone, TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        verify(mTelephonyManagerProxy, times(1)).unregisterTelephonyCallback(eq(callback));
        verify(testPhone, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK),
                any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));
    }

    /**
     * Test that startEmergencyCall() is called right after exiting ECM on the other slot.
     */
    @Test
    @SmallTest
    public void exitEcm_thenDialEmergencyCallOnTheOtherSlotRightAfter() throws Exception {
        // Setup EmergencyStateTracker
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        emergencyStateTracker.setPdnDisconnectionTimeoutMs(0);
        // Create test Phone
        Phone testPhone = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(testPhone, E_REG_RESULT);
        setUpAsyncResultForExitEmergencyMode(testPhone);

        verify(testPhone, times(0)).setEmergencyMode(anyInt(), any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));

        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(testPhone,
                TEST_CALL_ID, false);
        processAllMessages();

        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(TEST_CALL_ID);

        assertTrue(emergencyStateTracker.isInEcm());

        Context mockContext = mock(Context.class);
        replaceInstance(EmergencyStateTracker.class, "mContext",
                emergencyStateTracker, mockContext);
        processAllFutureMessages();

        ArgumentCaptor<TelephonyCallback> callbackCaptor =
                ArgumentCaptor.forClass(TelephonyCallback.class);

        verify(mTelephonyManagerProxy).registerTelephonyCallback(eq(testPhone.getSubId()),
                  any(), callbackCaptor.capture());

        TelephonyCallback callback = callbackCaptor.getValue();

        assertNotNull(callback);
        assertFalse(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        verify(mTelephonyManagerProxy, times(0)).unregisterTelephonyCallback(eq(callback));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK),
                any(Message.class));
        verify(testPhone, times(0)).exitEmergencyMode(any(Message.class));

        Phone phone1 = getPhone(1);
        verify(phone1, times(0)).setEmergencyMode(anyInt(), any(Message.class));
        verify(phone1, times(0)).exitEmergencyMode(any(Message.class));

        replaceInstance(EmergencyStateTracker.class, "mContext", emergencyStateTracker, mContext);

        // dial on the other slot
        unused = emergencyStateTracker.startEmergencyCall(phone1, TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertFalse(emergencyStateTracker.isInEcm());
        verify(mTelephonyManagerProxy, times(1)).unregisterTelephonyCallback(eq(callback));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(testPhone, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK),
                any(Message.class));
        verify(testPhone, times(1)).exitEmergencyMode(any(Message.class));
        verify(phone1, times(1)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        verify(phone1, times(0)).exitEmergencyMode(any(Message.class));
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
                TEST_CALL_ID, false);
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);

        processAllMessages();

        emergencyStateTracker.endCall(TEST_CALL_ID);

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
                TEST_CALL_ID, false);
        processAllMessages();
        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.onEmergencyCallDomainUpdated(
                PhoneConstants.PHONE_TYPE_IMS, TEST_CALL_ID);
        // Set ecm as supported
        setEcmSupportedConfig(testPhone, /* ecmSupported= */ true);
        // End call to enter ECM
        emergencyStateTracker.endCall(TEST_CALL_ID);
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
                TEST_CALL_ID, false);

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
                TEST_CALL_ID, true);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
        verify(phone0, never()).setEmergencyMode(anyInt(), any(Message.class));
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

        // Second starting call
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                TEST_CALL_ID_2, false);

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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.endCall(TEST_CALL_ID);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEcm());

        // Second emergency call started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone0,
                TEST_CALL_ID_2, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        // Second emergency call
        Phone phone1 = getPhone(1);
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencyCall(phone1,
                TEST_CALL_ID_2, false);

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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.endCall(TEST_CALL_ID);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        // Second emergency call started.
        future = emergencyStateTracker.startEmergencyCall(phone0, TEST_CALL_ID_2, false);

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WLAN);
        emergencyStateTracker.endCall(TEST_CALL_ID_2);
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
                TEST_CALL_ID, false);

        // Set emergency transport
        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_CALL, MODE_EMERGENCY_WLAN);

        // Set call properties
        emergencyStateTracker.onEmergencyCallPropertiesChanged(
                android.telecom.Connection.PROPERTY_WIFI, TEST_CALL_ID);

        verify(testPhone, times(0)).cancelEmergencyNetworkScan(anyBoolean(), any());

        // Set call to ACTIVE
        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

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

        assertTrue(emergencyStateTracker.getEmergencyRegResult().equals(E_REG_RESULT));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));
    }

    @Test
    @SmallTest
    public void testEndSms() {
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

        assertTrue(emergencyStateTracker.getEmergencyRegResult().equals(E_REG_RESULT));
        // Expect: DisconnectCause#NOT_DISCONNECTED.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true);

        verify(phone0).exitEmergencyMode(any(Message.class));
        assertFalse(emergencyStateTracker.isInEmergencyMode());
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
        assertTrue(emergencyStateTracker.getEmergencyRegResult().equals(E_REG_RESULT));
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);
        emergencyStateTracker.endCall(TEST_CALL_ID);

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
    public void testStartEmergencyCallActiveAndSmsOnSamePhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

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
                TEST_CALL_ID, false);
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
        future = emergencyStateTracker.startEmergencyCall(phone0, TEST_CALL_ID, false);
        processAllMessages();

        verify(phone0).exitEmergencyMode(any(Message.class));
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));
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
                TEST_CALL_ID, false);

        assertFalse(smsFuture.isDone());
        assertFalse(callFuture.isDone());

        // Response message for setEmergencyMode by SMS.
        Message msg = smsCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Exit emergency mode and set the emergency mode again by the call when the exit result
        // is received for obtaining the latest EmergencyRegResult.
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
    public void testStartEmergencyCallAndSmsOnDifferentPhone() {
        EmergencyStateTracker emergencyStateTracker = setupEmergencyStateTracker(
                /* isSuplDdsSwitchRequiredForEmergencyCall= */ true);
        Phone phone0 = setupTestPhoneForEmergencyCall(/* isRoaming= */ true,
                /* isRadioOn= */ true);
        setUpAsyncResultForSetEmergencyMode(phone0, E_REG_RESULT);
        // Emergency call is in active.
        CompletableFuture<Integer> unused = emergencyStateTracker.startEmergencyCall(phone0,
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

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
        future = emergencyStateTracker.startEmergencyCall(phone1, TEST_CALL_ID, false);
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
                TEST_CALL_ID, false);

        assertFalse(smsFuture.isDone());
        assertFalse(callFuture.isDone());

        // Response message for setEmergencyMode by SMS.
        Message msg = smsCaptor.getValue();
        AsyncResult.forMessage(msg, E_REG_RESULT, null);
        msg.sendToTarget();
        processAllMessages();

        // Exit emergency mode and set the emergency mode again by the call when the exit result
        // is received for obtaining the latest EmergencyRegResult.
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(TEST_CALL_ID);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        emergencyStateTracker.endSms(TEST_SMS_ID, true);
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        emergencyStateTracker.endCall(TEST_CALL_ID);
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(TEST_CALL_ID);

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        emergencyStateTracker.endSms(TEST_SMS_ID, true);
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endCall(TEST_CALL_ID);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEcm());
        assertFalse(emergencyStateTracker.isInEmergencyCall());

        emergencyStateTracker.onEmergencyTransportChanged(
                EmergencyStateTracker.EMERGENCY_TYPE_SMS, MODE_EMERGENCY_WWAN);
        emergencyStateTracker.endSms(TEST_SMS_ID, true);
        processAllMessages();

        // Enter emergency callback mode and emergency mode changed by SMS end.
        verify(phone0, times(2)).setEmergencyMode(eq(MODE_EMERGENCY_CALLBACK), any(Message.class));
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
                TEST_CALL_ID, false);
        processAllMessages();

        assertTrue(emergencyStateTracker.isInEmergencyMode());
        assertTrue(emergencyStateTracker.isInEmergencyCall());
        verify(phone0).setEmergencyMode(eq(MODE_EMERGENCY_WWAN), any(Message.class));

        emergencyStateTracker.onEmergencyCallStateChanged(Call.State.ACTIVE, TEST_CALL_ID);

        // Emergency SMS is being started.
        CompletableFuture<Integer> future = emergencyStateTracker.startEmergencySms(phone0,
                TEST_SMS_ID, false);

        // Returns DisconnectCause#NOT_DISCONNECTED immediately.
        assertEquals(future.getNow(DisconnectCause.ERROR_UNSPECIFIED),
                Integer.valueOf(DisconnectCause.NOT_DISCONNECTED));

        emergencyStateTracker.endSms(TEST_SMS_ID, true);

        assertTrue(emergencyStateTracker.isInEmergencyMode());

        emergencyStateTracker.endCall(TEST_CALL_ID);
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
    public void testSaveKeyEmergencyCallbackModeSupportedBool() {
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        Phone phone = setupTestPhoneForEmergencyCall(false /* isRoaming */,
                false /* isRadioOn */);
        when(phone.getSubId()).thenReturn(1);
        setEcmSupportedConfig(phone, true);

        EmergencyStateTracker testEst = setupEmergencyStateTracker(
                false /* isSuplDdsSwitchRequiredForEmergencyCall */);

        assertNotNull(testEst.startEmergencyCall(phone, TEST_CALL_ID, false));

        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);

        verify(cfgManager).registerCarrierConfigChangeListener(any(),
                listenerArgumentCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener carrierConfigChangeListener =
                listenerArgumentCaptor.getAllValues().get(0);

        // Verify carrier config for valid subscription
        assertTrue(testEst.isEmergencyCallbackModeSupported());

        // SIM removed
        when(phone.getSubId()).thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setEcmSupportedConfig(phone, false);

        // Verify default config for invalid subscription
        assertFalse(testEst.isEmergencyCallbackModeSupported());

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
        assertTrue(testEst.isEmergencyCallbackModeSupported());

        // Insert SIM again, but emergency callback mode not supported
        when(phone.getSubId()).thenReturn(1);
        setEcmSupportedConfig(phone, false);

        // onCarrierConfigChanged with valid subscription
        carrierConfigChangeListener.onCarrierConfigChanged(
                phone.getPhoneId(), phone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);

        // Verify carrier config for valid subscription
        assertFalse(testEst.isEmergencyCallbackModeSupported());

        // SIM removed again
        when(phone.getSubId()).thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setEcmSupportedConfig(phone, false);

        // onCarrierConfigChanged with invalid subscription
        carrierConfigChangeListener.onCarrierConfigChanged(
                phone.getPhoneId(), phone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);

        // Verify saved config for valid subscription
        assertFalse(testEst.isEmergencyCallbackModeSupported());
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
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(phone.getSubId()).putBoolean(
                CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALLBACK_MODE_SUPPORTED_BOOL,
                ecmSupported);
    }

    private void setConfigForDdsSwitch(Phone phone, String[] roaminPlmns,
            int suplEmergencyModeType, String esExtensionSec) {
        CarrierConfigManager cfgManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgManager.getConfigForSubId(phone.getSubId()).putStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY,
                roaminPlmns);
        cfgManager.getConfigForSubId(phone.getSubId()).putInt(
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                suplEmergencyModeType);
        cfgManager.getConfigForSubId(phone.getSubId())
                .putString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, esExtensionSec);
    }

    private void setUpAsyncResultForSetEmergencyMode(Phone phone, EmergencyRegResult regResult) {
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
}
