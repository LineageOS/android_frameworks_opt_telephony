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

package com.android.internal.telephony.metrics;

import static android.telephony.SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER;
import static android.telephony.SubscriptionManager.PHONE_NUMBER_SOURCE_IMS;
import static android.telephony.SubscriptionManager.PHONE_NUMBER_SOURCE_UICC;
import static android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_GSM;

import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_A;
import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_B;
import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__WFC_MODE__CELLULAR_PREFERRED;
import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__WFC_MODE__UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__WFC_MODE__WIFI_ONLY;
import static com.android.internal.telephony.TelephonyStatsLog.PER_SIM_STATUS__WFC_MODE__WIFI_PREFERRED;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PerSimStatusTest extends TelephonyTest {
    private static final int ENABLED_2G =
            (int) TelephonyManager.NETWORK_TYPE_BITMASK_GSM
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_GPRS
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_CDMA
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT;

    private Phone mSecondPhone;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSecondPhone = mock(Phone.class);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void onPullAtom_perSimStatus() throws Exception {
        // Make PhoneFactory.getPhones() return an array of two
        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone, mSecondPhone});
        // phone 0 setup
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(1).when(mPhone).getSubId();
        doReturn(100).when(mPhone).getCarrierId();
        doReturn("6506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_UICC, null, null);
        doReturn("")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_CARRIER, null, null);
        doReturn("+16506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_IMS, null, null);
        SubscriptionInfo subscriptionInfo1 = mock(SubscriptionInfo.class);
        doReturn("us").when(subscriptionInfo1).getCountryIso();
        doReturn(subscriptionInfo1).when(mSubscriptionController).getSubscriptionInfo(1);
        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        ImsMmTelManager imsMmTelManager1 = mock(ImsMmTelManager.class);
        doReturn(imsMmTelManager1).when(imsManager).getImsMmTelManager(1);
        doReturn(true).when(imsMmTelManager1).isAdvancedCallingSettingEnabled();
        doReturn(true).when(imsMmTelManager1).isVoWiFiSettingEnabled();
        doReturn(ImsMmTelManager.WIFI_MODE_WIFI_ONLY).when(imsMmTelManager1).getVoWiFiModeSetting();
        doReturn(ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED)
                .when(imsMmTelManager1)
                .getVoWiFiRoamingModeSetting();
        doReturn(false).when(imsMmTelManager1).isVtSettingEnabled();
        doReturn(false).when(mPhone).getDataRoamingEnabled();
        doReturn(1L)
                .when(mPhone)
                .getAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(0L)
                .when(mPhone)
                .getAllowedNetworkTypes(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G & ENABLED_2G);
        IccCard iccCard1 = mock(IccCard.class);
        doReturn(true).when(iccCard1).getIccLockEnabled();
        doReturn(iccCard1).when(mPhone).getIccCard();
        UiccSlot uiccSlot1 = mock(UiccSlot.class);
        doReturn(UiccSlot.VOLTAGE_CLASS_A).when(uiccSlot1).getMinimumVoltageClass();
        doReturn(uiccSlot1).when(mUiccController).getUiccSlotForPhone(0);
        doReturn(NETWORK_TYPE_BITMASK_GSM).when(mPersistAtomsStorage).getUnmeteredNetworks(0, 100);
        // phone 1 setup
        doReturn(mContext).when(mSecondPhone).getContext();
        doReturn(1).when(mSecondPhone).getPhoneId();
        doReturn(2).when(mSecondPhone).getSubId();
        doReturn(101).when(mSecondPhone).getCarrierId();
        doReturn("0123")
                .when(mSubscriptionController)
                .getPhoneNumber(2, PHONE_NUMBER_SOURCE_UICC, null, null);
        doReturn("16506950123")
                .when(mSubscriptionController)
                .getPhoneNumber(2, PHONE_NUMBER_SOURCE_CARRIER, null, null);
        doReturn("+16506950123")
                .when(mSubscriptionController)
                .getPhoneNumber(2, PHONE_NUMBER_SOURCE_IMS, null, null);
        SubscriptionInfo subscriptionInfo2 = mock(SubscriptionInfo.class);
        doReturn("us").when(subscriptionInfo2).getCountryIso();
        doReturn(subscriptionInfo2).when(mSubscriptionController).getSubscriptionInfo(2);
        ImsMmTelManager imsMmTelManager2 = mock(ImsMmTelManager.class);
        doReturn(imsMmTelManager2).when(imsManager).getImsMmTelManager(2);
        doReturn(true).when(imsMmTelManager2).isAdvancedCallingSettingEnabled();
        doReturn(false).when(imsMmTelManager2).isVoWiFiSettingEnabled();
        doReturn(ImsMmTelManager.WIFI_MODE_WIFI_ONLY).when(imsMmTelManager2).getVoWiFiModeSetting();
        doReturn(ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED)
                .when(imsMmTelManager2)
                .getVoWiFiRoamingModeSetting();
        doReturn(true).when(imsMmTelManager2).isVtSettingEnabled();
        doReturn(false).when(mSecondPhone).getDataRoamingEnabled();
        doReturn(1L)
                .when(mSecondPhone)
                .getAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(1L)
                .when(mSecondPhone)
                .getAllowedNetworkTypes(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G & ENABLED_2G);
        IccCard iccCard2 = mock(IccCard.class);
        doReturn(false).when(iccCard2).getIccLockEnabled();
        doReturn(iccCard2).when(mSecondPhone).getIccCard();
        UiccSlot uiccSlot2 = mock(UiccSlot.class);
        doReturn(UiccSlot.VOLTAGE_CLASS_B).when(uiccSlot2).getMinimumVoltageClass();
        doReturn(uiccSlot2).when(mUiccController).getUiccSlotForPhone(1);
        doReturn(NETWORK_TYPE_BITMASK_GSM).when(mPersistAtomsStorage).getUnmeteredNetworks(1, 101);

        PerSimStatus perSimStatus1 = PerSimStatus.getCurrentState(mPhone);
        PerSimStatus perSimStatus2 = PerSimStatus.getCurrentState(mSecondPhone);

        assertEquals(100, perSimStatus1.carrierId);
        assertEquals(1, perSimStatus1.phoneNumberSourceUicc);
        assertEquals(0, perSimStatus1.phoneNumberSourceCarrier);
        assertEquals(1, perSimStatus1.phoneNumberSourceIms);
        assertEquals(true, perSimStatus1.advancedCallingSettingEnabled);
        assertEquals(true, perSimStatus1.voWiFiSettingEnabled);
        assertEquals(PER_SIM_STATUS__WFC_MODE__WIFI_ONLY, perSimStatus1.voWiFiModeSetting);
        assertEquals(
                PER_SIM_STATUS__WFC_MODE__CELLULAR_PREFERRED,
                perSimStatus1.voWiFiRoamingModeSetting);
        assertEquals(false, perSimStatus1.vtSettingEnabled);
        assertEquals(false, perSimStatus1.dataRoamingEnabled);
        assertEquals(1L, perSimStatus1.preferredNetworkType);
        assertEquals(true, perSimStatus1.disabled2g);
        assertEquals(true, perSimStatus1.pin1Enabled);
        assertEquals(
                PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_A,
                perSimStatus1.minimumVoltageClass);
        assertEquals(NETWORK_TYPE_BITMASK_GSM, perSimStatus1.unmeteredNetworks);
        assertEquals(101, perSimStatus2.carrierId);
        assertEquals(1, perSimStatus2.phoneNumberSourceUicc);
        assertEquals(2, perSimStatus2.phoneNumberSourceCarrier);
        assertEquals(2, perSimStatus2.phoneNumberSourceIms);
        assertEquals(true, perSimStatus2.advancedCallingSettingEnabled);
        assertEquals(false, perSimStatus2.voWiFiSettingEnabled);
        assertEquals(PER_SIM_STATUS__WFC_MODE__WIFI_ONLY, perSimStatus2.voWiFiModeSetting);
        assertEquals(
                PER_SIM_STATUS__WFC_MODE__WIFI_PREFERRED, perSimStatus2.voWiFiRoamingModeSetting);
        assertEquals(true, perSimStatus2.vtSettingEnabled);
        assertEquals(false, perSimStatus2.dataRoamingEnabled);
        assertEquals(1L, perSimStatus2.preferredNetworkType);
        assertEquals(false, perSimStatus2.disabled2g);
        assertEquals(false, perSimStatus2.pin1Enabled);
        assertEquals(
                PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_B,
                perSimStatus2.minimumVoltageClass);
        assertEquals(NETWORK_TYPE_BITMASK_GSM, perSimStatus2.unmeteredNetworks);
    }

    @Test
    @SmallTest
    public void onPullAtom_perSimStatus_noSubscriptionController() throws Exception {
        replaceInstance(SubscriptionController.class, "sInstance", null, null);

        PerSimStatus perSimStatus = PerSimStatus.getCurrentState(mPhone);

        assertEquals(perSimStatus, null);
    }

    @Test
    @SmallTest
    public void onPullAtom_perSimStatus_noImsManager() throws Exception {
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(1).when(mPhone).getSubId();
        doReturn(100).when(mPhone).getCarrierId();
        doReturn("6506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_UICC, null, null);
        doReturn("")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_CARRIER, null, null);
        doReturn("+16506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_IMS, null, null);
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        doReturn("us").when(subscriptionInfo).getCountryIso();
        doReturn(subscriptionInfo).when(mSubscriptionController).getSubscriptionInfo(1);
        doReturn(null).when(mContext).getSystemService(ImsManager.class);
        doReturn(1L)
                .when(mPhone)
                .getAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(0L)
                .when(mPhone)
                .getAllowedNetworkTypes(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G & ENABLED_2G);
        IccCard iccCard = mock(IccCard.class);
        doReturn(true).when(iccCard).getIccLockEnabled();
        doReturn(iccCard).when(mPhone).getIccCard();
        UiccSlot uiccSlot1 = mock(UiccSlot.class);
        doReturn(UiccSlot.VOLTAGE_CLASS_A).when(uiccSlot1).getMinimumVoltageClass();
        doReturn(uiccSlot1).when(mUiccController).getUiccSlotForPhone(0);
        doReturn(NETWORK_TYPE_BITMASK_GSM).when(mPersistAtomsStorage).getUnmeteredNetworks(0, 100);

        PerSimStatus perSimStatus = PerSimStatus.getCurrentState(mPhone);

        assertEquals(100, perSimStatus.carrierId);
        assertEquals(1, perSimStatus.phoneNumberSourceUicc);
        assertEquals(0, perSimStatus.phoneNumberSourceCarrier);
        assertEquals(1, perSimStatus.phoneNumberSourceIms);
        assertEquals(false, perSimStatus.advancedCallingSettingEnabled);
        assertEquals(false, perSimStatus.voWiFiSettingEnabled);
        assertEquals(PER_SIM_STATUS__WFC_MODE__UNKNOWN, perSimStatus.voWiFiModeSetting);
        assertEquals(PER_SIM_STATUS__WFC_MODE__UNKNOWN, perSimStatus.voWiFiRoamingModeSetting);
        assertEquals(false, perSimStatus.vtSettingEnabled);
        assertEquals(false, perSimStatus.dataRoamingEnabled);
        assertEquals(1L, perSimStatus.preferredNetworkType);
        assertEquals(true, perSimStatus.disabled2g);
        assertEquals(true, perSimStatus.pin1Enabled);
        assertEquals(
                PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_A,
                perSimStatus.minimumVoltageClass);
        assertEquals(NETWORK_TYPE_BITMASK_GSM, perSimStatus.unmeteredNetworks);
    }

    @Test
    @SmallTest
    public void onPullAtom_perSimStatus_noImsMmTelManager() throws Exception {
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(1).when(mPhone).getSubId();
        doReturn(100).when(mPhone).getCarrierId();
        doReturn("6506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_UICC, null, null);
        doReturn("")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_CARRIER, null, null);
        doReturn("+16506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_IMS, null, null);
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        doReturn("us").when(subscriptionInfo).getCountryIso();
        doReturn(subscriptionInfo).when(mSubscriptionController).getSubscriptionInfo(1);
        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        doThrow(new IllegalArgumentException()).when(imsManager).getImsMmTelManager(1);
        doReturn(false).when(mPhone).getDataRoamingEnabled();
        doReturn(1L)
                .when(mPhone)
                .getAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(0L)
                .when(mPhone)
                .getAllowedNetworkTypes(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G & ENABLED_2G);
        IccCard iccCard = mock(IccCard.class);
        doReturn(true).when(iccCard).getIccLockEnabled();
        doReturn(iccCard).when(mPhone).getIccCard();
        UiccSlot uiccSlot1 = mock(UiccSlot.class);
        doReturn(UiccSlot.VOLTAGE_CLASS_A).when(uiccSlot1).getMinimumVoltageClass();
        doReturn(uiccSlot1).when(mUiccController).getUiccSlotForPhone(0);
        doReturn(NETWORK_TYPE_BITMASK_GSM).when(mPersistAtomsStorage).getUnmeteredNetworks(0, 100);

        PerSimStatus perSimStatus = PerSimStatus.getCurrentState(mPhone);

        assertEquals(100, perSimStatus.carrierId);
        assertEquals(1, perSimStatus.phoneNumberSourceUicc);
        assertEquals(0, perSimStatus.phoneNumberSourceCarrier);
        assertEquals(1, perSimStatus.phoneNumberSourceIms);
        assertEquals(false, perSimStatus.advancedCallingSettingEnabled);
        assertEquals(false, perSimStatus.voWiFiSettingEnabled);
        assertEquals(PER_SIM_STATUS__WFC_MODE__UNKNOWN, perSimStatus.voWiFiModeSetting);
        assertEquals(PER_SIM_STATUS__WFC_MODE__UNKNOWN, perSimStatus.voWiFiRoamingModeSetting);
        assertEquals(false, perSimStatus.vtSettingEnabled);
        assertEquals(false, perSimStatus.dataRoamingEnabled);
        assertEquals(1L, perSimStatus.preferredNetworkType);
        assertEquals(true, perSimStatus.disabled2g);
        assertEquals(true, perSimStatus.pin1Enabled);
        assertEquals(
                PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_A,
                perSimStatus.minimumVoltageClass);
        assertEquals(NETWORK_TYPE_BITMASK_GSM, perSimStatus.unmeteredNetworks);
    }

    @Test
    @SmallTest
    public void onPullAtom_perSimStatus_noUiccSlot() throws Exception {
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(1).when(mPhone).getSubId();
        doReturn(100).when(mPhone).getCarrierId();
        doReturn("6506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_UICC, null, null);
        doReturn("")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_CARRIER, null, null);
        doReturn("+16506953210")
                .when(mSubscriptionController)
                .getPhoneNumber(1, PHONE_NUMBER_SOURCE_IMS, null, null);
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        doReturn("us").when(subscriptionInfo).getCountryIso();
        doReturn(subscriptionInfo).when(mSubscriptionController).getSubscriptionInfo(1);
        doReturn(null).when(mContext).getSystemService(ImsManager.class);
        doReturn(1L)
                .when(mPhone)
                .getAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(0L)
                .when(mPhone)
                .getAllowedNetworkTypes(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G & ENABLED_2G);
        IccCard iccCard = mock(IccCard.class);
        doReturn(true).when(iccCard).getIccLockEnabled();
        doReturn(iccCard).when(mPhone).getIccCard();
        doReturn(null).when(mUiccController).getUiccSlotForPhone(0);
        doReturn(NETWORK_TYPE_BITMASK_GSM).when(mPersistAtomsStorage).getUnmeteredNetworks(0, 100);

        PerSimStatus perSimStatus = PerSimStatus.getCurrentState(mPhone);

        assertEquals(100, perSimStatus.carrierId);
        assertEquals(1, perSimStatus.phoneNumberSourceUicc);
        assertEquals(0, perSimStatus.phoneNumberSourceCarrier);
        assertEquals(1, perSimStatus.phoneNumberSourceIms);
        assertEquals(false, perSimStatus.advancedCallingSettingEnabled);
        assertEquals(false, perSimStatus.voWiFiSettingEnabled);
        assertEquals(PER_SIM_STATUS__WFC_MODE__UNKNOWN, perSimStatus.voWiFiModeSetting);
        assertEquals(PER_SIM_STATUS__WFC_MODE__UNKNOWN, perSimStatus.voWiFiRoamingModeSetting);
        assertEquals(false, perSimStatus.vtSettingEnabled);
        assertEquals(false, perSimStatus.dataRoamingEnabled);
        assertEquals(1L, perSimStatus.preferredNetworkType);
        assertEquals(true, perSimStatus.disabled2g);
        assertEquals(true, perSimStatus.pin1Enabled);
        assertEquals(
                PER_SIM_STATUS__SIM_VOLTAGE_CLASS__VOLTAGE_CLASS_UNKNOWN,
                perSimStatus.minimumVoltageClass);
        assertEquals(NETWORK_TYPE_BITMASK_GSM, perSimStatus.unmeteredNetworks);
    }
}
