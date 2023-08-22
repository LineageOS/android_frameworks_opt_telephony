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
package com.android.internal.telephony.subscription;

import static com.google.common.truth.Truth.assertThat;

import android.os.ParcelUuid;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.UiccAccessRule;
import android.telephony.ims.ImsMmTelManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubscriptionInfoInternalTest {
    private final SubscriptionInfoInternal mSubInfo =
            new SubscriptionInfoInternal.Builder()
                    .setId(1)
                    .setIccId(SubscriptionDatabaseManagerTest.FAKE_ICCID1)
                    .setSimSlotIndex(0)
                    .setDisplayName(SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1)
                    .setCarrierName(SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1)
                    .setDisplayNameSource(SubscriptionManager.NAME_SOURCE_SIM_SPN)
                    .setIconTint(SubscriptionDatabaseManagerTest.FAKE_COLOR1)
                    .setNumber(SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1)
                    .setDataRoaming(SubscriptionManager.DATA_ROAMING_ENABLE)
                    .setMcc(SubscriptionDatabaseManagerTest.FAKE_MCC1)
                    .setMnc(SubscriptionDatabaseManagerTest.FAKE_MNC1)
                    .setEhplmns(SubscriptionDatabaseManagerTest.FAKE_EHPLMNS1)
                    .setHplmns(SubscriptionDatabaseManagerTest.FAKE_HPLMNS1)
                    .setEmbedded(1)
                    .setCardString(SubscriptionDatabaseManagerTest.FAKE_ICCID1)
                    .setCardId(1)
                    .setNativeAccessRules(SubscriptionDatabaseManagerTest
                            .FAKE_NATIVE_ACCESS_RULES1)
                    .setCarrierConfigAccessRules(SubscriptionDatabaseManagerTest
                            .FAKE_CARRIER_CONFIG_ACCESS_RULES1)
                    .setRemovableEmbedded(0)
                    .setCellBroadcastExtremeThreatAlertEnabled(1)
                    .setCellBroadcastSevereThreatAlertEnabled(1)
                    .setCellBroadcastAmberAlertEnabled(1)
                    .setCellBroadcastEmergencyAlertEnabled(1)
                    .setCellBroadcastAlertSoundDuration(4)
                    .setCellBroadcastAlertReminderInterval(1)
                    .setCellBroadcastAlertVibrationEnabled(1)
                    .setCellBroadcastAlertSpeechEnabled(1)
                    .setCellBroadcastEtwsTestAlertEnabled(1)
                    .setCellBroadcastAreaInfoMessageEnabled(1)
                    .setCellBroadcastTestAlertEnabled(1)
                    .setCellBroadcastOptOutDialogEnabled(1)
                    .setEnhanced4GModeEnabled(1)
                    .setVideoTelephonyEnabled(1)
                    .setWifiCallingEnabled(1)
                    .setWifiCallingMode(ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED)
                    .setWifiCallingModeForRoaming(ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED)
                    .setWifiCallingEnabledForRoaming(1)
                    .setOpportunistic(0)
                    .setGroupUuid(SubscriptionDatabaseManagerTest.FAKE_UUID1)
                    .setCountryIso(SubscriptionDatabaseManagerTest.FAKE_COUNTRY_CODE1)
                    .setCarrierId(SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID1)
                    .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                    .setType(SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)
                    .setGroupOwner(SubscriptionDatabaseManagerTest.FAKE_OWNER1)
                    .setEnabledMobileDataPolicies(SubscriptionDatabaseManagerTest
                            .FAKE_MOBILE_DATA_POLICY1)
                    .setImsi(SubscriptionDatabaseManagerTest.FAKE_IMSI1)
                    .setUiccApplicationsEnabled(1)
                    .setRcsUceEnabled(1)
                    .setCrossSimCallingEnabled(1)
                    .setRcsConfig(SubscriptionDatabaseManagerTest.FAKE_RCS_CONFIG1)
                    .setAllowedNetworkTypesForReasons(SubscriptionDatabaseManagerTest
                            .FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1)
                    .setDeviceToDeviceStatusSharingPreference(
                            SubscriptionManager.D2D_SHARING_ALL_CONTACTS)
                    .setVoImsOptInEnabled(1)
                    .setDeviceToDeviceStatusSharingContacts(
                            SubscriptionDatabaseManagerTest.FAKE_CONTACT1)
                    .setNrAdvancedCallingEnabled(1)
                    .setNumberFromCarrier(SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1)
                    .setNumberFromIms(SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1)
                    .setPortIndex(0)
                    .setUsageSetting(SubscriptionManager.USAGE_SETTING_DEFAULT)
                    .setLastUsedTPMessageReference(SubscriptionDatabaseManagerTest
                            .FAKE_TP_MESSAGE_REFERENCE1)
                    .setUserId(SubscriptionDatabaseManagerTest.FAKE_USER_ID1)
                    .setSatelliteEnabled(1)
                    .setGroupDisabled(false)
                    .build();

    private final SubscriptionInfoInternal mSubInfoNull =
            new SubscriptionInfoInternal.Builder()
                    .setId(1)
                    .setIccId("123")
                    .setSimSlotIndex(0)
                    .setDisplayName("")
                    .setCarrierName("")
                    .setNumber("")
                    .setMcc("")
                    .setMnc("")
                    .setEhplmns("")
                    .setHplmns("")
                    .setEmbedded(1)
                    .setCardId(1)
                    .setNativeAccessRules(new byte[0])
                    .setCarrierConfigAccessRules(new byte[0])
                    .setGroupUuid("")
                    .setCountryIso("")
                    .setGroupOwner("")
                    .setEnabledMobileDataPolicies("")
                    .setImsi("")
                    .setRcsConfig(new byte[0])
                    .setAllowedNetworkTypesForReasons("")
                    .setDeviceToDeviceStatusSharingContacts("")
                    .build();

    @Test
    public void testSubscriptionInfoInternalSetAndGet() {
        assertThat(mSubInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(mSubInfo.getIccId()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_ICCID1);
        assertThat(mSubInfo.getSimSlotIndex()).isEqualTo(0);
        assertThat(mSubInfo.getDisplayName()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1);
        assertThat(mSubInfo.getCarrierName()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1);
        assertThat(mSubInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_SIM_SPN);
        assertThat(mSubInfo.getIconTint()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_COLOR1);
        assertThat(mSubInfo.getNumber()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1);
        assertThat(mSubInfo.getDataRoaming()).isEqualTo(SubscriptionManager.DATA_ROAMING_ENABLE);
        assertThat(mSubInfo.getMcc()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_MCC1);
        assertThat(mSubInfo.getMnc()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_MNC1);
        assertThat(mSubInfo.getEhplmns()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_EHPLMNS1);
        assertThat(mSubInfo.getHplmns()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_HPLMNS1);
        assertThat(mSubInfo.getEmbedded()).isEqualTo(1);
        assertThat(mSubInfo.getCardString()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_ICCID1);
        assertThat(mSubInfo.getCardId()).isEqualTo(1);
        assertThat(mSubInfo.getNativeAccessRules()).isEqualTo(SubscriptionDatabaseManagerTest
                .FAKE_NATIVE_ACCESS_RULES1);
        assertThat(mSubInfo.getCarrierConfigAccessRules()).isEqualTo(SubscriptionDatabaseManagerTest
                .FAKE_CARRIER_CONFIG_ACCESS_RULES1);
        assertThat(mSubInfo.getRemovableEmbedded()).isEqualTo(0);
        assertThat(mSubInfo.getCellBroadcastExtremeThreatAlertEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastSevereThreatAlertEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastAmberAlertEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastEmergencyAlertEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastAlertSoundDuration()).isEqualTo(4);
        assertThat(mSubInfo.getCellBroadcastAlertReminderInterval()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastAlertVibrationEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastAlertSpeechEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastEtwsTestAlertEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastAreaInfoMessageEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastTestAlertEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCellBroadcastOptOutDialogEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getEnhanced4GModeEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getVideoTelephonyEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getWifiCallingEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getWifiCallingMode()).isEqualTo(
                ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED);
        assertThat(mSubInfo.getWifiCallingModeForRoaming()).isEqualTo(
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED);
        assertThat(mSubInfo.getOpportunistic()).isEqualTo(0);
        assertThat(mSubInfo.getGroupUuid()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_UUID1);
        assertThat(mSubInfo.getCountryIso()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_COUNTRY_CODE1);
        assertThat(mSubInfo.getCarrierId()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID1);
        assertThat(mSubInfo.getProfileClass()).isEqualTo(
                SubscriptionManager.PROFILE_CLASS_OPERATIONAL);
        assertThat(mSubInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        assertThat(mSubInfo.getGroupOwner()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_OWNER1);
        assertThat(mSubInfo.getEnabledMobileDataPolicies()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_MOBILE_DATA_POLICY1);
        assertThat(mSubInfo.getImsi()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_IMSI1);
        assertThat(mSubInfo.getUiccApplicationsEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getRcsUceEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getCrossSimCallingEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getRcsConfig()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_RCS_CONFIG1);
        assertThat(mSubInfo.getAllowedNetworkTypesForReasons()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1);
        assertThat(mSubInfo.getVoImsOptInEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getDeviceToDeviceStatusSharingContacts()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CONTACT1);
        assertThat(mSubInfo.getNrAdvancedCallingEnabled()).isEqualTo(1);
        assertThat(mSubInfo.getNumberFromCarrier()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1);
        assertThat(mSubInfo.getNumberFromIms()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1);
        assertThat(mSubInfo.getPortIndex()).isEqualTo(
                SubscriptionManager.USAGE_SETTING_DEFAULT);
        assertThat(mSubInfo.getLastUsedTPMessageReference()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_TP_MESSAGE_REFERENCE1);
        assertThat(mSubInfo.getUserId()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_USER_ID1);
        assertThat(mSubInfo.getSatelliteEnabled()).isEqualTo(1);
        assertThat(mSubInfo.isGroupDisabled()).isFalse();
    }

    @Test
    public void testEquals() {
        SubscriptionInfoInternal another = new SubscriptionInfoInternal.Builder(mSubInfo).build();
        assertThat(another).isEqualTo(mSubInfo);
        assertThat(another.hashCode()).isEqualTo(mSubInfo.hashCode());
    }

    @Test
    public void testConvertToSubscriptionInfo() {
        SubscriptionInfo subInfo = mSubInfo.toSubscriptionInfo();

        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getIccId()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_ICCID1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfo.getDisplayName()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1);
        assertThat(subInfo.getCarrierName()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1);
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_SIM_SPN);
        assertThat(subInfo.getIconTint()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_COLOR1);
        assertThat(subInfo.getNumber()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1);
        assertThat(subInfo.getDataRoaming()).isEqualTo(SubscriptionManager.DATA_ROAMING_ENABLE);
        assertThat(subInfo.getMccString()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_MCC1);
        assertThat(subInfo.getMncString()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_MNC1);
        assertThat(subInfo.getEhplmns()).isEqualTo(Arrays.asList(
                SubscriptionDatabaseManagerTest.FAKE_EHPLMNS1.split(",")));
        assertThat(subInfo.getHplmns()).isEqualTo(Arrays.asList(
                SubscriptionDatabaseManagerTest.FAKE_HPLMNS1.split(",")));
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.getCardString()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_ICCID1);
        assertThat(subInfo.getCardId()).isEqualTo(1);


        List<UiccAccessRule> rules = new ArrayList<>();

        rules.addAll(Arrays.asList(UiccAccessRule.decodeRules(
                SubscriptionDatabaseManagerTest.FAKE_NATIVE_ACCESS_RULES1)));
        rules.addAll(Arrays.asList(UiccAccessRule.decodeRules(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_CONFIG_ACCESS_RULES1)));

        assertThat(subInfo.getAccessRules()).containsExactlyElementsIn(rules);

        assertThat(subInfo.isOpportunistic()).isFalse();
        assertThat(subInfo.getGroupUuid()).isEqualTo(ParcelUuid.fromString(
                SubscriptionDatabaseManagerTest.FAKE_UUID1));
        assertThat(subInfo.getCountryIso()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_COUNTRY_CODE1);
        assertThat(subInfo.getCarrierId()).isEqualTo(
                SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID1);
        assertThat(subInfo.getProfileClass()).isEqualTo(
                SubscriptionManager.PROFILE_CLASS_OPERATIONAL);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        assertThat(subInfo.getGroupOwner()).isEqualTo(SubscriptionDatabaseManagerTest.FAKE_OWNER1);
        assertThat(subInfo.areUiccApplicationsEnabled()).isTrue();
        assertThat(subInfo.getPortIndex()).isEqualTo(
                SubscriptionManager.USAGE_SETTING_DEFAULT);
        assertThat(subInfo.isGroupDisabled()).isFalse();
    }

    @Test
    public void testNullability() {
        SubscriptionInfo subInfoNull = mSubInfoNull.toSubscriptionInfo();
        assertThat(subInfoNull.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfoNull.getIccId()).isEqualTo("123");
        assertThat(subInfoNull.getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfoNull.getDisplayName()).isEqualTo("");
        assertThat(subInfoNull.getCarrierName()).isEqualTo("");
        assertThat(subInfoNull.getNumber()).isEqualTo("");
        assertThat(subInfoNull.getMccString()).isEqualTo("");
        assertThat(subInfoNull.getMncString()).isEqualTo("");
        assertThat(subInfoNull.getEhplmns()).isEmpty();
        assertThat(subInfoNull.getHplmns()).isEmpty();
        assertThat(subInfoNull.isEmbedded()).isTrue();
        assertThat(subInfoNull.getCardId()).isEqualTo(1);
        assertThat(subInfoNull.getAccessRules()).isNull();
        assertThat(subInfoNull.getGroupUuid()).isNull();
        assertThat(subInfoNull.getCountryIso()).isEqualTo("");
        assertThat(subInfoNull.getGroupOwner()).isEqualTo("");
    }
}
