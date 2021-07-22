/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityLte;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneCapability;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CellularNetworkValidatorTest extends TelephonyTest {
    private CellularNetworkValidator mValidatorUT;
    private static final PhoneCapability CAPABILITY_WITH_VALIDATION_SUPPORTED =
            new PhoneCapability(1, 1, null, true, new int[0]);
    private static final PhoneCapability CAPABILITY_WITHOUT_VALIDATION_SUPPORTED =
            new PhoneCapability(1, 1, null, false, new int[0]);
    private final CellIdentityLte mCellIdentityLte1 = new CellIdentityLte(123, 456, 0, 0, 111);
    private final CellIdentityLte mCellIdentityLte2 = new CellIdentityLte(321, 654, 0, 0, 222);
    @Mock
    CellularNetworkValidator.ValidationCallback mCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        doReturn(CAPABILITY_WITH_VALIDATION_SUPPORTED).when(mPhoneConfigurationManager)
                .getCurrentPhoneCapability();
        mValidatorUT = new CellularNetworkValidator(mContext);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        processAllMessages();
        setCacheTtlInCarrierConfig(5000);
    }

    @After
    public void tearDown() throws Exception {
        mValidatorUT.stopValidation();
        super.tearDown();
    }

    /**
     * Test that a single phone case results in our phone being active and the RIL called
     */
    @Test
    @SmallTest
    public void testValidationSupported() {
        doReturn(CAPABILITY_WITH_VALIDATION_SUPPORTED).when(mPhoneConfigurationManager)
                .getCurrentPhoneCapability();
        assertTrue(mValidatorUT.isValidationFeatureSupported());

        doReturn(CAPABILITY_WITHOUT_VALIDATION_SUPPORTED).when(mPhoneConfigurationManager)
                .getCurrentPhoneCapability();
        assertFalse(mValidatorUT.isValidationFeatureSupported());
    }

    /**
     * Test that a single phone case results in our phone being active and the RIL called
     */
    @Test
    @SmallTest
    public void testValidateSuccess() {
        int subId = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);

        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertValidationResult(subId, true);
    }

    @Test
    @SmallTest
    public void testValidateTimeout() {
        int subId = 1;
        int timeout = 100;
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);

        // Wait for timeout
        moveTimeForward(timeout);
        processAllMessages();
        assertValidationResult(subId, false);
    }

    @Test
    @SmallTest
    public void testValidateFailure() {
        int subId = 1;
        int timeout = 100;
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);
        mValidatorUT.mNetworkCallback.onUnavailable();
        assertValidationResult(subId, false);
    }

    @Test
    @SmallTest
    public void testNetworkAvailableNotValidated() {
        int subId = 1;
        int timeout = 100;
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);

        mValidatorUT.mNetworkCallback.onAvailable(new Network(100));
        assertInValidation(subId);

        // Wait for timeout
        moveTimeForward(timeout);
        processAllMessages();

        assertValidationResult(subId, false);
    }

    @Test
    @SmallTest
    public void testSkipRecentlyValidatedNetwork() {
        int subId = 1;
        int timeout = 1000;
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        testValidateSuccess();

        resetStates();
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);

        // As recently validated, onAvailable should trigger switch.
        mValidatorUT.mNetworkCallback.onAvailable(new Network(100));
        assertValidationResult(subId, true);
    }

    @Test
    @SmallTest
    public void testDoNotSkipIfValidationFailed() {
        int subId = 1;
        int timeout = 1000;
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        testValidateFailure();

        resetStates();
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);
        // Last time validation fialed, onAvailable should NOT trigger switch.
        mValidatorUT.mNetworkCallback.onAvailable(new Network(100));
        assertInValidation(subId);
    }

    @Test
    @SmallTest
    public void testDoNotSkipIfCacheExpires() {
        int subId = 1;
        int timeout = 1000;
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
        testValidateSuccess();

        // Mark mValidationCacheTtl to only 1 second.
        setCacheTtlInCarrierConfig(1000);
        waitForMs(1100);

        resetStates();
        mValidatorUT.validate(subId, timeout, true, mCallback);
        assertInValidation(subId);

        // Last time validation expired, onAvailable should NOT trigger switch.
        mValidatorUT.mNetworkCallback.onAvailable(new Network(100));
        assertInValidation(subId);
    }

    @Test
    @SmallTest
    public void testNetworkCachingOfMultipleSub() {
        int timeout = 1000;
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        assertNetworkRecentlyValidated(1, false);
        assertNetworkRecentlyValidated(2, false);
        assertNetworkRecentlyValidated(3, false);
        // Validate sub 1, 2, and 3.
        mValidatorUT.validate(1, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertValidationResult(1, true);
        assertNetworkRecentlyValidated(1, true);
        assertNetworkRecentlyValidated(2, false);
        assertNetworkRecentlyValidated(3, false);
        mValidatorUT.validate(2, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertValidationResult(2, true);
        mValidatorUT.validate(3, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertValidationResult(3, true);
        assertNetworkRecentlyValidated(1, true);
        assertNetworkRecentlyValidated(2, true);
        assertNetworkRecentlyValidated(3, true);

        // When re-validating sub 3, onAvailable should trigger validation callback.
        resetStates();
        mValidatorUT.validate(3, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onAvailable(new Network(100));
        assertValidationResult(3, true);
        // Mark sub 2 validation failed. Should clear the network from cache.
        resetStates();
        mValidatorUT.validate(2, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onLost(new Network(100));
        assertNetworkRecentlyValidated(2, false);

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());
    }

    @Test
    @SmallTest
    public void testNetworkCachingOfMultipleNetworks() {
        int timeout = 1000;
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        // Validate sub 1.
        assertNetworkRecentlyValidated(1, false);
        mValidatorUT.validate(1, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertNetworkRecentlyValidated(1, true);

        // Change reg state to a different network.
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte2)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        // Should NOT skip validation.
        assertNetworkRecentlyValidated(1, false);
    }

    @Test
    @SmallTest
    public void testNetworkCachingOverflow() {
        int timeout = 1000;
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setCellIdentity(mCellIdentityLte1)
                .build();
        doReturn(mNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                anyInt(), anyInt());

        for (int subId = 8; subId <= 100; subId++) {
            mValidatorUT.validate(subId, timeout, true, mCallback);
            mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            assertNetworkRecentlyValidated(subId, true);
        }

        // Last 10 subs are kept in cache.
        for (int subId = 1; subId <= 90; subId++) {
            assertNetworkRecentlyValidated(subId, false);
        }
        // Last 10 subs are kept in cache.
        for (int subId = 91; subId <= 100; subId++) {
            assertNetworkRecentlyValidated(subId, true);
        }
    }

    @Test
    @SmallTest
    public void testOnNetworkAvailable() {
        int subId = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId, timeout, true, mCallback);
        Network network = new Network(100);
        mValidatorUT.mNetworkCallback.onAvailable(network);

        assertInValidation(subId);
        verify(mCallback).onNetworkAvailable(network, subId);
    }

    @Test
    @SmallTest
    public void testReleaseRequestAfterValidation_shouldReleaseImmediately() {
        int subId = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId, timeout, true, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertValidationResult(subId, true);
    }

    @Test
    @SmallTest
    public void testDoNotReleaseRequestAfterValidation_shouldReleaseLater() {
        int subId = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId, timeout, false, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        verify(mCallback).onValidationDone(true, subId);
        assertInValidation(subId);
        moveTimeForward(1000);
        processAllMessages();
        assertValidationResult(subId, true);
    }

    @Test
    @SmallTest
    public void testDoNotReleaseRequestAfterValidation_validationFails_shouldReleaseImmediately() {
        int subId = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId, timeout, false, mCallback);
        mValidatorUT.mNetworkCallback.onLost(new Network(100));
        assertValidationResult(subId, false);
    }

    @Test
    @SmallTest
    public void testDoNotReleaseRequestAfterValidation_timeout_shouldReleaseImmediately() {
        int subId = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId, timeout, false, mCallback);
        assertInValidation(subId);
        moveTimeForward(timeout);
        processAllMessages();
        assertValidationResult(subId, false);
    }

    @Test
    @SmallTest
    public void testDoNotReleaseRequestAfterValidation_BackToBackRequest() {
        int subId1 = 1;
        int timeout = 1000;
        mValidatorUT.validate(subId1, timeout, false, mCallback);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        verify(mCallback).onValidationDone(true, subId1);
        assertInValidation(subId1);

        resetStates();
        int subId2 = 2;
        mValidatorUT.validate(subId2, timeout, false, mCallback);
        assertInValidation(subId2);
        mValidatorUT.mNetworkCallback.onCapabilitiesChanged(null, new NetworkCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        verify(mCallback).onValidationDone(true, subId2);
        assertInValidation(subId2);
        moveTimeForward(1000);
        processAllMessages();
        assertValidationResult(subId2, true);
        // No callback should be triggered on subId1
        verify(mCallback, never()).onValidationDone(anyBoolean(), eq(subId1));
    }

    private void assertNetworkRecentlyValidated(int subId, boolean shouldBeRecentlyValidated) {
        // Start validation and send network available callback.
        resetStates();
        mValidatorUT.validate(subId, 1000, true, mCallback);
        mValidatorUT.mNetworkCallback.onAvailable(new Network(1000));

        if (shouldBeRecentlyValidated) {
            assertValidationResult(subId, true);
        } else {
            assertInValidation(subId);
        }

        mValidatorUT.stopValidation();
        resetStates();
    }

    private void assertValidationResult(int subId, boolean shouldPass) {
        // Verify that validation is over.
        verify(mConnectivityManager).unregisterNetworkCallback(eq(mValidatorUT.mNetworkCallback));
        assertFalse(mValidatorUT.mHandler.hasMessagesOrCallbacks());
        assertFalse(mValidatorUT.isValidating());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mValidatorUT.getSubIdInValidation());

        // Verify result.
        verify(mCallback).onValidationDone(shouldPass, subId);
    }

    private void assertInValidation(int subId) {
        assertEquals(subId, mValidatorUT.getSubIdInValidation());
        assertTrue(mValidatorUT.mHandler.hasMessagesOrCallbacks());
        assertEquals(subId, mValidatorUT.getSubIdInValidation());
        NetworkRequest expectedRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                        .setSubscriptionId(subId).build())
                .build();
        verify(mConnectivityManager).requestNetwork(
                eq(expectedRequest), eq(mValidatorUT.mNetworkCallback), any());
        assertTrue(mValidatorUT.isValidating());
    }

    private void resetStates() {
        clearInvocations(mConnectivityManager);
        clearInvocations(mCallback);
    }

    private void setCacheTtlInCarrierConfig(long ttl) {
        // Mark to skip validation in 5 seconds.
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(anyInt());
        bundle.putLong(CarrierConfigManager.KEY_DATA_SWITCH_VALIDATION_MIN_GAP_LONG, ttl);
    }
}
