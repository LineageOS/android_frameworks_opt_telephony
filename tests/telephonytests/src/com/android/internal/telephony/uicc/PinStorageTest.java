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
package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.uicc.IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PinStorageTest extends TelephonyTest {
    private static final String ICCID_1 = "89010003006562472370";
    private static final String ICCID_2 = "89010003006562472399";
    private static final String ICCID_INVALID = "1234";

    private int mBootCount;
    private int mSimulatedRebootsCount;
    private PinStorage mPinStorage;

    private void simulateReboot() {
        mSimulatedRebootsCount++;
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.BOOT_COUNT, mBootCount + mSimulatedRebootsCount);

        mPinStorage = new PinStorage(mContext);
        mPinStorage.mShortTermSecretKeyDurationMinutes = 0;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        // Store boot count, so that correct value can be restored at the end.
        mBootCount = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        mSimulatedRebootsCount = 0;

        // Clear shared preferences.
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getContext())
                .edit().clear().commit();
        // Enable PIN storage in resources
        mContextFixture.putBooleanResource(
                R.bool.config_allow_pin_storage_for_unattended_reboot, true);
        // Remaining setup
        doReturn(ICCID_1).when(mPhone).getFullIccSerialNumber();
        // Simulate the device is not secure by default
        when(mKeyguardManager.isDeviceSecure()).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked()).thenReturn(false);

        mPinStorage = new PinStorage(mContext);
        mPinStorage.mShortTermSecretKeyDurationMinutes = 0;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Restore boot count
        if (mBootCount == -1) {
            Settings.Global.resetToDefaults(
                    mContext.getContentResolver(), Settings.Global.BOOT_COUNT);
        } else {
            Settings.Global.putInt(
                    mContext.getContentResolver(), Settings.Global.BOOT_COUNT, mBootCount);
        }
    }

    @Test
    @SmallTest
    public void storePin_withoutReboot_pinCannotBeRetrieved() {
        mPinStorage.storePin("1234", 0);

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_normalReboot_pinCannotBeRetrieved() {
        mPinStorage.storePin("1234", 0);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_crash_pinCannotBeRetrieved() {
        mPinStorage.storePin("1234", 0);

        // Simulate crash
        mPinStorage = new PinStorage(mContext);
        mPinStorage.mShortTermSecretKeyDurationMinutes = 0;

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_unattendedReboot_pinCanBeRetrievedOnce() {
        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        // PIN can be retrieved only once after unattended reboot
        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("1234");
        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_unattendedReboot_deviceIsLocked() {
        // Simulate the device is still locked
        when(mKeyguardManager.isDeviceSecure()).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked()).thenReturn(true);
        simulateReboot();

        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_ERROR);

        simulateReboot();

        // PIN cannot  be retrieved
        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_unattendedReboot_pinIsRemovedAfterDelay() {
        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        // Move time forward by 60 seconds
        moveTimeForward(60000);
        processAllMessages();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");

        // Simulate a second unattended reboot to make sure that PIN was deleted.
        result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_unattendedRebootNotDone_pinCannotBeRetrieved() {
        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        // Move time forward by 60 seconds before simulating reboot
        moveTimeForward(60000);
        processAllMessages();
        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_unattendedReboot_iccidChange() {
        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        // Switch to a different ICCID in the device after the reboot
        doReturn(ICCID_2).when(mPhone).getFullIccSerialNumber();

        assertThat(mPinStorage.getPin(0, ICCID_2)).isEqualTo("");

        // Switch back to the initial ICCID to make sure that PIN was deleted.
        doReturn(ICCID_1).when(mPhone).getFullIccSerialNumber();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void clearPin_pinCannotBeRetrieved() {
        mPinStorage.storePin("1234", 0);
        mPinStorage.clearPin(0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_pinChanged_pinIsUpdated() {
        mPinStorage.storePin("1234", 0);
        mPinStorage.storePin("5678", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("5678");
    }

    @Test
    @SmallTest
    public void storePin_pinTooShort_pinIsNotStored() {
        mPinStorage.storePin("12", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_pinTooLong_pinIsNotStored() {
        mPinStorage.storePin("123456789", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_invalidIccid_pinIsNotStored() {
        doReturn(ICCID_INVALID).when(mPhone).getFullIccSerialNumber();

        mPinStorage.storePin("1234", 0);
        int result = mPinStorage.prepareUnattendedReboot();

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_INVALID)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_disabledInResources_pinIsNotStored() {
        mContextFixture.putBooleanResource(
                R.bool.config_allow_pin_storage_for_unattended_reboot, false);

        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_disabledInResources_containsSimWithPinEnabledAndVerified() {
        mContextFixture.putBooleanResource(
                R.bool.config_allow_pin_storage_for_unattended_reboot, false);

        when(mUiccController.getUiccProfileForPhone(anyInt())).thenReturn(mUiccProfile);
        when(mUiccCardApplication3gpp.getPin1State()).thenReturn(PINSTATE_ENABLED_VERIFIED);

        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_PIN_REQUIRED);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_disabledInCarrierConfig_pinIsNotStored() {
        PersistableBundle carrierConfigs = new PersistableBundle();
        carrierConfigs.putBoolean(
                CarrierConfigManager.KEY_STORE_SIM_PIN_FOR_UNATTENDED_REBOOT_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(carrierConfigs);

        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_changeToDisabledInCarrierConfig_pinIsRemoved() {
        mPinStorage.storePin("1234", 0);

        // Simulate change in the carrier configuration
        PersistableBundle carrierConfigs = new PersistableBundle();
        carrierConfigs.putBoolean(
                CarrierConfigManager.KEY_STORE_SIM_PIN_FOR_UNATTENDED_REBOOT_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(carrierConfigs);
        final Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, 0);
        mContext.sendBroadcast(intent);
        processAllMessages();

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_simIsRemoved_pinIsRemoved() {
        mPinStorage.storePin("1234", 0);

        // SIM is removed
        final Intent intent = new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        intent.putExtra(PhoneConstants.PHONE_KEY, 0);
        intent.putExtra(TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_ABSENT);
        mContext.sendBroadcast(intent);
        processAllMessages();

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }

    @Test
    @SmallTest
    public void storePin_simReadyAfterUnattendedReboot_pinIsRemoved() {
        mPinStorage.storePin("1234", 0);

        int result = mPinStorage.prepareUnattendedReboot();
        assertThat(result).isEqualTo(TelephonyManager.PREPARE_UNATTENDED_REBOOT_SUCCESS);

        simulateReboot();

        // SIM is fully loaded before cached PIN is used.
        final Intent intent = new Intent(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        intent.putExtra(PhoneConstants.PHONE_KEY, 0);
        intent.putExtra(TelephonyManager.EXTRA_SIM_STATE, TelephonyManager.SIM_STATE_LOADED);
        mContext.sendBroadcast(intent);
        processAllMessages();

        assertThat(mPinStorage.getPin(0, ICCID_1)).isEqualTo("");
    }
}
