/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyTest;

import com.google.i18n.phonenumbers.ShortNumberInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for EmergencyNumberTracker.java
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class EmergencyNumberTrackerTest extends TelephonyTest {

    private static final String LOCAL_DOWNLOAD_DIRECTORY = "Download/Emergency_number_db_unit_test";
    private static final String EMERGENCY_NUMBER_DB_OTA_FILE = "eccdata_ota";
    private static final int CONFIG_UNIT_TEST_EMERGENCY_NUMBER_DB_VERSION = 99999;
    private static final String CONFIG_EMERGENCY_NUMBER_ADDRESS = "54321";
    private static final String CONFIG_EMERGENCY_DUPLICATE_NUMBER = "4321";
    private static final String CONFIG_EMERGENCY_NUMBER_COUNTRY = "us";
    private static final String CONFIG_EMERGENCY_NUMBER_MNC = "";
    private static final String NON_3GPP_EMERGENCY_TEST_NUMBER = "9876543";
    private static final int CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES =
            EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                    | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                    | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE
                    | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                    | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE
                    | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC
                    | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC;
    private static final ArrayList<String> CONFIG_EMERGENCY_NUMBER_SERVICE_URNS =
            new ArrayList<String>();
    private static final EmergencyNumber CONFIG_EMERGENCY_NUMBER = new EmergencyNumber(
            CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    CONFIG_EMERGENCY_NUMBER_MNC, CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
    private static final int OTA_UNIT_TEST_EMERGENCY_NUMBER_DB_VERSION = 999999;
    private static final String OTA_EMERGENCY_NUMBER_ADDRESS = "98765";
    private static final int SUB_ID_PHONE_1 = 1;
    private static final int SUB_ID_PHONE_2 = 2;
    private static final int VALID_SLOT_INDEX_VALID_1 = 0;
    private static final int VALID_SLOT_INDEX_VALID_2 = 1;
    private static final int INVALID_SLOT_INDEX_VALID = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    private ParcelFileDescriptor mOtaParcelFileDescriptor = null;
    // Mocked classes
    private CarrierConfigManager mCarrierConfigManagerMock;

    // mEmergencyNumberTrackerMock for mPhone
    private EmergencyNumberTracker mEmergencyNumberTrackerMock;
    // mEmergencyNumberTrackerMock2 for mPhone2
    private EmergencyNumberTracker mEmergencyNumberTrackerMock2;

    private List<EmergencyNumber> mEmergencyNumberListTestSample = new ArrayList<>();
    private EmergencyNumber mUsEmergencyNumber;
    private String[] mEmergencyNumberPrefixTestSample = {"123", "456"};

    private File mLocalDownloadDirectory;
    private ShortNumberInfo mShortNumberInfo;
    private Context mMockContext;
    private Resources mResources;

    @Before
    public void setUp() throws Exception {
        logd("EmergencyNumberTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mShortNumberInfo = mock(ShortNumberInfo.class);
        mCarrierConfigManagerMock = mock(CarrierConfigManager.class);

        mContext = new ContextWrapper(InstrumentationRegistry.getTargetContext());
        mMockContext = mock(Context.class);
        mResources = mock(Resources.class);

        doReturn(mContext).when(mPhone).getContext();
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(SUB_ID_PHONE_1).when(mPhone).getSubId();

        doReturn(mContext).when(mPhone2).getContext();
        doReturn(1).when(mPhone2).getPhoneId();
        doReturn(SUB_ID_PHONE_2).when(mPhone2).getSubId();

        initializeEmergencyNumberListTestSamples();
        mEmergencyNumberTrackerMock = new EmergencyNumberTracker(mPhone, mSimulatedCommands);
        mEmergencyNumberTrackerMock2 = new EmergencyNumberTracker(mPhone2, mSimulatedCommands);
        doReturn(mEmergencyNumberTrackerMock2).when(mPhone2).getEmergencyNumberTracker();
        mEmergencyNumberTrackerMock.DBG = true;

        // Copy an OTA file to the test directory to similate the OTA mechanism
        simulateOtaEmergencyNumberDb(mPhone);

        AssetManager am = new AssetManager.Builder().build();
        doReturn(am).when(mMockContext).getAssets();

        processAllMessages();
        logd("EmergencyNumberTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        // Set back to single sim mode
        setSinglePhone();
        Path target = Paths.get(mLocalDownloadDirectory.getPath(), EMERGENCY_NUMBER_DB_OTA_FILE);
        Files.deleteIfExists(target);
        mLocalDownloadDirectory.delete();
        mLocalDownloadDirectory = null;
        mEmergencyNumberTrackerMock = null;
        mEmergencyNumberTrackerMock2 = null;
        mEmergencyNumberListTestSample.clear();
        mEmergencyNumberListTestSample = null;
        if (mOtaParcelFileDescriptor != null) {
            try {
                mOtaParcelFileDescriptor.close();
                mOtaParcelFileDescriptor = null;
            } catch (IOException e) {
                logd("Failed to close emergency number db file folder for testing " + e.toString());
            }
        }
        super.tearDown();
    }

    private void initializeEmergencyNumberListTestSamples() {
        EmergencyNumber emergencyNumberForTest = new EmergencyNumber("119", "jp", "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        mUsEmergencyNumber = new EmergencyNumber("911", "us", "",
            EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE, new ArrayList<String>(),
            EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
            EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        mEmergencyNumberListTestSample.add(emergencyNumberForTest);

        emergencyNumberForTest = new EmergencyNumber(
                CONFIG_EMERGENCY_DUPLICATE_NUMBER, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        mEmergencyNumberListTestSample.add(emergencyNumberForTest);
    }

    private void sendEmergencyNumberListFromRadio() {
        mEmergencyNumberTrackerMock.sendMessage(
                mEmergencyNumberTrackerMock.obtainMessage(
                        1 /* EVENT_UNSOL_EMERGENCY_NUMBER_LIST */,
                        new AsyncResult(null, mEmergencyNumberListTestSample, null)));
        processAllMessages();
    }

    private void sendEmptyEmergencyNumberListFromRadio(
        EmergencyNumberTracker emergencyNumberTrackerMock) {
        emergencyNumberTrackerMock.sendMessage(
            emergencyNumberTrackerMock.obtainMessage(
                1 /* EVENT_UNSOL_EMERGENCY_NUMBER_LIST */,
                new AsyncResult(null, new ArrayList<>(), null)));
        processAllMessages();
    }

    private void cacheEmergencyNumberListFromDatabaseByCountry(String countryIso) {
        mEmergencyNumberTrackerMock.updateEmergencyNumberDatabaseCountryChange(countryIso);
        processAllMessages();
    }

    private void sendEmergencyNumberPrefix(EmergencyNumberTracker emergencyNumberTrackerMock) {
        emergencyNumberTrackerMock.obtainMessage(
        	4 /* EVENT_UPDATE_EMERGENCY_NUMBER_PREFIX */,
                mEmergencyNumberPrefixTestSample).sendToTarget();
        processAllMessages();
    }

    private void setOtaEmergencyNumberDbFileFolderForTesting(
            EmergencyNumberTracker emergencyNumberTrackerMock, Phone phone) {
        // Override the OTA emergency number database file path for testing
        File file = new File(Environment.getExternalStorageDirectory(), LOCAL_DOWNLOAD_DIRECTORY
                + "/" + EMERGENCY_NUMBER_DB_OTA_FILE);
        try {
            mOtaParcelFileDescriptor = ParcelFileDescriptor.open(
                    file, ParcelFileDescriptor.MODE_READ_ONLY);
            emergencyNumberTrackerMock.obtainMessage(
                EmergencyNumberTracker.EVENT_OVERRIDE_OTA_EMERGENCY_NUMBER_DB_FILE_PATH,
                    mOtaParcelFileDescriptor).sendToTarget();
            logd("Changed emergency number db file folder for testing ");
        } catch (FileNotFoundException e) {
            logd("Failed to open emergency number db file folder for testing " + e.toString());
        }
        processAllMessages();
    }

    private void resetOtaEmergencyNumberDbFileFolderForTesting(
            EmergencyNumberTracker emergencyNumberTrackerMock) {
        emergencyNumberTrackerMock.obtainMessage(
                EmergencyNumberTracker.EVENT_OVERRIDE_OTA_EMERGENCY_NUMBER_DB_FILE_PATH, null)
                        .sendToTarget();
        processAllMessages();
    }

    private void sendOtaEmergencyNumberDb(EmergencyNumberTracker emergencyNumberTrackerMock) {
        emergencyNumberTrackerMock.obtainMessage(
                EmergencyNumberTracker.EVENT_UPDATE_OTA_EMERGENCY_NUMBER_DB).sendToTarget();
        processAllMessages();
    }

    /**
     * Copy an OTA file to the test directory to similate the OTA mechanism.
     *
     * Version: 999999
     * Number: US, 98765, POLICE | AMBULANCE | FIRE
     */
    private void simulateOtaEmergencyNumberDb(Phone phone) {
        try {
            mLocalDownloadDirectory = new File(
                    Environment.getExternalStorageDirectory(), LOCAL_DOWNLOAD_DIRECTORY);
            mLocalDownloadDirectory.mkdir();
            final Path target = Paths.get(
                    mLocalDownloadDirectory.getPath(), EMERGENCY_NUMBER_DB_OTA_FILE);
            Files.deleteIfExists(target);
            final InputStream source = new BufferedInputStream(
                    phone.getContext().getAssets().open(EMERGENCY_NUMBER_DB_OTA_FILE));
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            logd("Copied test OTA database file to " + target);
        } catch (Exception e) {
            logd("Unable to copy downloaded file " + e);
        }
    }

    private boolean hasDbEmergencyNumbers(List<EmergencyNumber> subList,
            List<EmergencyNumber> list) {
        return list.containsAll(subList);
    }

    private boolean hasDbEmergencyNumber(EmergencyNumber number, List<EmergencyNumber> list) {
        return list.contains(number);
    }

    private boolean hasDbEmergencyNumber(String number, List<EmergencyNumber> list) {
        boolean foundDbNumber = false;
        for (EmergencyNumber num : list) {
            if (num.getNumber().equals(number)) {
                foundDbNumber = true;
            }
        }
        return foundDbNumber;
    }

    private void setDsdsPhones() throws Exception {
        mPhones = new Phone[] {mPhone, mPhone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
    }

    private void setSinglePhone() throws Exception {
        mPhones = new Phone[] {mPhone};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
    }

    /**
     * Test EmergencyNumberTracker.isSimAbsent().
     */
    @Test
    public void testIsSimAbsent() throws Exception {
        setDsdsPhones();
        doReturn(VALID_SLOT_INDEX_VALID_1).when(mSubscriptionManagerService).getSlotIndex(
                eq(SUB_ID_PHONE_1));
        doReturn(VALID_SLOT_INDEX_VALID_2).when(mSubscriptionManagerService).getSlotIndex(
                eq(SUB_ID_PHONE_2));
        assertFalse(mEmergencyNumberTrackerMock.isSimAbsent());

        // One sim slot is active; the other one is not active
        doReturn(VALID_SLOT_INDEX_VALID_1).when(mSubscriptionManagerService).getSlotIndex(
                eq(SUB_ID_PHONE_1));
        doReturn(INVALID_SLOT_INDEX_VALID).when(mSubscriptionManagerService).getSlotIndex(
                eq(SUB_ID_PHONE_2));
        assertFalse(mEmergencyNumberTrackerMock.isSimAbsent());

        // Both sim slots are not active
        doReturn(INVALID_SLOT_INDEX_VALID).when(mSubscriptionManagerService).getSlotIndex(
                anyInt());
        assertTrue(mEmergencyNumberTrackerMock.isSimAbsent());
    }

    @Test
    public void testEmergencyNumberListFromRadio() throws Exception {
        sendEmergencyNumberListFromRadio();
        assertEquals(mEmergencyNumberListTestSample,
                mEmergencyNumberTrackerMock.getRadioEmergencyNumberList());
    }

    @Test
    public void testRegistrationForCountryChangeIntent() throws Exception {
        EmergencyNumberTracker localEmergencyNumberTracker;
        Context spyContext = spy(mContext);
        doReturn(spyContext).when(mPhone).getContext();
        ArgumentCaptor<IntentFilter> intentCaptor = ArgumentCaptor.forClass(IntentFilter.class);

        localEmergencyNumberTracker = new EmergencyNumberTracker(mPhone, mSimulatedCommands);
        verify(spyContext, times(1)).registerReceiver(any(), intentCaptor.capture());
        IntentFilter ifilter = intentCaptor.getValue();
        assertTrue(ifilter.hasAction(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED));
    }

    @Test
    public void testUpdateEmergencyCountryIso_whenStatePowerOff() throws Exception {
        testUpdateEmergencyCountryIso(ServiceState.STATE_POWER_OFF);
    }

    @Test
    public void testUpdateEmergencyCountryIso_whenStateInService() throws Exception {
        testUpdateEmergencyCountryIso(ServiceState.STATE_IN_SERVICE);
    }

    @Test
    public void testUpdateEmergencyCountryIso_whenStateOos() throws Exception {
        testUpdateEmergencyCountryIso(ServiceState.STATE_OUT_OF_SERVICE);
    }

    @Test
    public void testUpdateEmergencyCountryIso_whenStateEmergencyOnly() throws Exception {
        testUpdateEmergencyCountryIso(ServiceState.STATE_EMERGENCY_ONLY);
    }

    private void testUpdateEmergencyCountryIso(int ss) throws Exception {
        doReturn(mLocaleTracker).when(mSST).getLocaleTracker();
        doReturn("us").when(mLocaleTracker).getLastKnownCountryIso();

        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock);

        mEmergencyNumberTrackerMock.updateEmergencyNumberDatabaseCountryChange("us");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals("us"));
        assertTrue(mEmergencyNumberTrackerMock.getLastKnownEmergencyCountryIso().equals("us"));

        doReturn(ss).when(mServiceState).getState();
        mEmergencyNumberTrackerMock.updateEmergencyNumberDatabaseCountryChange("");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals(""));
        assertTrue(mEmergencyNumberTrackerMock.getLastKnownEmergencyCountryIso().equals("us"));

        //make sure we look up cached location whenever current iso is null
        verify(mLocaleTracker).getLastKnownCountryIso();
    }

    @Test
    public void testUpdateEmergencyCountryIsoMultiSim() throws Exception {
        setDsdsPhones();
        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock);
        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock2);

        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("jp");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals("jp"));
        assertTrue(mEmergencyNumberTrackerMock.getLastKnownEmergencyCountryIso().equals("jp"));
        assertTrue(mEmergencyNumberTrackerMock2.getEmergencyCountryIso().equals("jp"));
        assertTrue(mEmergencyNumberTrackerMock2.getLastKnownEmergencyCountryIso().equals("jp"));

        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals(""));
        assertTrue(mEmergencyNumberTrackerMock.getLastKnownEmergencyCountryIso().equals("jp"));
        assertTrue(mEmergencyNumberTrackerMock2.getEmergencyCountryIso().equals("jp"));
        assertTrue(mEmergencyNumberTrackerMock2.getLastKnownEmergencyCountryIso().equals("jp"));
    }

    @Test
    public void testIsEmergencyNumber_FallbackToShortNumberXml_NoSims() throws Exception {
        setDsdsPhones();

        // Both sim slots are not active
        doReturn(INVALID_SLOT_INDEX_VALID).when(mSubscriptionManagerService).getSlotIndex(
                anyInt());
        assertTrue(mEmergencyNumberTrackerMock.isSimAbsent());

        sendEmptyEmergencyNumberListFromRadio(mEmergencyNumberTrackerMock);
        sendEmptyEmergencyNumberListFromRadio(mEmergencyNumberTrackerMock2);

        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("JP");
        processAllMessages();

        replaceInstance(ShortNumberInfo.class, "INSTANCE", null, mShortNumberInfo);
        mEmergencyNumberTrackerMock.isEmergencyNumber(NON_3GPP_EMERGENCY_TEST_NUMBER);

        //verify that we fall back to shortnumber xml when there are no SIMs
        verify(mShortNumberInfo).isEmergencyNumber(NON_3GPP_EMERGENCY_TEST_NUMBER, "JP");
    }

    @Test
    public void testIsEmergencyNumber_NoFallbackToShortNumberXml_OneSimActive() throws Exception {
        testIsEmergencyNumber_NoFallbackToShortNumberXml(1);
    }

    @Test
    public void testIsEmergencyNumber_NoFallbackToShortNumberXml_TwoSimsActive() throws Exception {
        testIsEmergencyNumber_NoFallbackToShortNumberXml(2);
    }

    private void testIsEmergencyNumber_NoFallbackToShortNumberXml(int numSims) throws Exception {
        assertTrue((numSims > 0 && numSims < 3));
        setDsdsPhones();

        if (numSims == 1) {
            // One sim slot is active; the other one is not active
            doReturn(VALID_SLOT_INDEX_VALID_1).when(mSubscriptionManagerService).getSlotIndex(
                    eq(SUB_ID_PHONE_1));
            doReturn(INVALID_SLOT_INDEX_VALID).when(mSubscriptionManagerService).getSlotIndex(
                    eq(SUB_ID_PHONE_2));
        } else {
            //both slots active
            doReturn(VALID_SLOT_INDEX_VALID_1).when(mSubscriptionManagerService).getSlotIndex(
                    eq(SUB_ID_PHONE_1));
            doReturn(VALID_SLOT_INDEX_VALID_2).when(mSubscriptionManagerService).getSlotIndex(
                    eq(SUB_ID_PHONE_2));
        }
        assertFalse(mEmergencyNumberTrackerMock.isSimAbsent());

        //still send empty list from modem for both sims, else we always end up using that
        sendEmptyEmergencyNumberListFromRadio(mEmergencyNumberTrackerMock);
        sendEmptyEmergencyNumberListFromRadio(mEmergencyNumberTrackerMock2);

        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("JP");
        processAllMessages();

        replaceInstance(ShortNumberInfo.class, "INSTANCE", null, mShortNumberInfo);
        mEmergencyNumberTrackerMock.isEmergencyNumber(NON_3GPP_EMERGENCY_TEST_NUMBER);

        //verify we do not use ShortNumber xml
        verify(mShortNumberInfo, never()).isEmergencyNumber(anyString(), anyString());

    }

    @Test
    public void testUpdateEmergencyCountryIsoFromAnotherSimOrNot() throws Exception {
        setDsdsPhones();
        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock);
        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock2);

        // First, both slots have empty country iso, trigger a country change to "jp".
        // We should expect both sims have "jp" country iso.
        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("jp");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals("jp"));
        assertTrue(mEmergencyNumberTrackerMock2.getEmergencyCountryIso().equals("jp"));

        // Second, both slots now have "jp" country iso, trigger a country change to "us".
        // We should expect both sims have "us" country iso.
        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("us");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals("us"));
        assertTrue(mEmergencyNumberTrackerMock2.getEmergencyCountryIso().equals("us"));

        // Third, both slots now have "us" country iso, manually configure
        // "mIsCountrySetByAnotherSub" flag in "mPhone2" as false, and trigger a country
        // change to "ca". We should expect the current phone to change the country iso
        // to "ca", and should expect the other phone *not* to change their country iso
        // to "ca".
        mEmergencyNumberTrackerMock2.mIsCountrySetByAnotherSub = false;
        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("ca");
        processAllMessages();
        assertTrue(mEmergencyNumberTrackerMock.getEmergencyCountryIso().equals("ca"));
        assertTrue(mEmergencyNumberTrackerMock2.getEmergencyCountryIso().equals("us"));
    }

    /**
     * In 1.4 or above HAL, we should use database number.
     */
    @Test
    public void testUsingEmergencyNumberDatabaseWheneverHal_1_4() {
        doReturn(mMockContext).when(mPhone).getContext();
        doReturn(mContext.getAssets()).when(mMockContext).getAssets();
        doReturn(mResources).when(mMockContext).getResources();
        doReturn(true).when(mResources).getBoolean(
                com.android.internal.R.bool.ignore_emergency_number_routing_from_db);

        EmergencyNumberTracker emergencyNumberTrackerMock = new EmergencyNumberTracker(
                mPhone, mSimulatedCommands);
        emergencyNumberTrackerMock.sendMessage(
                emergencyNumberTrackerMock.obtainMessage(
                        1 /* EVENT_UNSOL_EMERGENCY_NUMBER_LIST */,
                        new AsyncResult(null, mEmergencyNumberListTestSample, null)));
        sendEmergencyNumberPrefix(emergencyNumberTrackerMock);
        emergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("us");
        processAllMessages();
        /* case 1: check DB number exist or not */
        assertTrue(hasDbEmergencyNumber(CONFIG_EMERGENCY_NUMBER,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        /* case 2: since ignore_emergency_routing_from_db is true. check for all DB numbers with
        routing value as unknown by ignoring DB value */
        List<EmergencyNumber> completeEmergencyNumberList = new ArrayList<>();
        EmergencyNumber emergencyNumber = new EmergencyNumber(
                "888", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        completeEmergencyNumberList.add(emergencyNumber);

        emergencyNumber = new EmergencyNumber(
                "54321", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        completeEmergencyNumberList.add(emergencyNumber);

        emergencyNumber = new EmergencyNumber(
                "654321", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        completeEmergencyNumberList.add(emergencyNumber);

        emergencyNumber = new EmergencyNumber(
                "7654321", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        completeEmergencyNumberList.add(emergencyNumber);

        assertTrue(hasDbEmergencyNumbers(completeEmergencyNumberList,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        /* case 3: check the routing type of merged duplicate numbers
            between DB number and radio list. */
        EmergencyNumber duplicateEmergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_DUPLICATE_NUMBER, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        assertTrue(hasDbEmergencyNumber(duplicateEmergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));
    }

    @Test
    public void testUsingEmergencyNumberDatabaseWithRouting() {
        doReturn(mMockContext).when(mPhone).getContext();
        doReturn(mContext.getAssets()).when(mMockContext).getAssets();
        doReturn(mResources).when(mMockContext).getResources();
        doReturn("05").when(mCellIdentity).getMncString();
        doReturn(false).when(mResources).getBoolean(
                com.android.internal.R.bool.ignore_emergency_number_routing_from_db);

        EmergencyNumberTracker emergencyNumberTrackerMock = new EmergencyNumberTracker(
                mPhone, mSimulatedCommands);
        emergencyNumberTrackerMock.sendMessage(
                emergencyNumberTrackerMock.obtainMessage(
                        1 /* EVENT_UNSOL_EMERGENCY_NUMBER_LIST */,
                        new AsyncResult(null, mEmergencyNumberListTestSample, null)));
        sendEmergencyNumberPrefix(emergencyNumberTrackerMock);
        emergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("us");
        processAllMessages();

        // case 1: check DB number with normal routing true and for mnc 05
        EmergencyNumber emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "05", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        // case 2: check DB number with normal routing true in multiple mnc 05, 45, 47
        emergencyNumber = new EmergencyNumber(
                "888", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "05", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        doReturn("47").when(mCellIdentity).getMncString();
        emergencyNumber = new EmergencyNumber(
                "888", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "47", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                        CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                            EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        /* case 3: check DB number with normal routing false and for mnc 05,
            but current cell identity is 04 */
        doReturn("04").when(mCellIdentity).getMncString();
        emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        // case 4: check DB number with normal routing false
        emergencyNumber = new EmergencyNumber(
                "654321", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        // case 5: check DB number with normal routing true & empty mnc
        emergencyNumber = new EmergencyNumber(
                "7654321", CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        /* case 6: check DB number with normal routing true & empty mnc. But same number exist
            in radio list. In merge DB routing should be used */
        emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_DUPLICATE_NUMBER, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));
    }

    @Test
    public void testUsingEmergencyNumberDatabaseWithRoutingInOOS() {
        doReturn(mMockContext).when(mPhone).getContext();
        doReturn(mContext.getAssets()).when(mMockContext).getAssets();
        doReturn(mResources).when(mMockContext).getResources();
        doReturn(false).when(mResources).getBoolean(
                com.android.internal.R.bool.ignore_emergency_number_routing_from_db);

        EmergencyNumberTracker emergencyNumberTrackerMock = new EmergencyNumberTracker(
                mPhone, mSimulatedCommands);
        emergencyNumberTrackerMock.sendMessage(
                emergencyNumberTrackerMock.obtainMessage(
                        1 /* EVENT_UNSOL_EMERGENCY_NUMBER_LIST */,
                        new AsyncResult(null, mEmergencyNumberListTestSample, null)));
        sendEmergencyNumberPrefix(emergencyNumberTrackerMock);
        emergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("us");
        processAllMessages();

        // Check routing when cellidentity is null, which is oos
        doReturn(null).when(mPhone).getCurrentCellIdentity();
        EmergencyNumber emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        // Check routing when cellidentity is 04, which is not part of normal routing mncs
        doReturn(mCellIdentity).when(mPhone).getCurrentCellIdentity();
        doReturn("04").when(mCellIdentity).getMncString();
        emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));

        // Check routing when cellidentity is 05, which is part of normal routing mncs
        doReturn("05").when(mCellIdentity).getMncString();
        emergencyNumber = new EmergencyNumber(
                CONFIG_EMERGENCY_NUMBER_ADDRESS, CONFIG_EMERGENCY_NUMBER_COUNTRY,
                    "05", CONFIG_EMERGENCY_NUMBER_SERVICE_CATEGORIES,
                            CONFIG_EMERGENCY_NUMBER_SERVICE_URNS,
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                                            EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertTrue(hasDbEmergencyNumber(emergencyNumber,
                emergencyNumberTrackerMock.getEmergencyNumberList()));
    }

    /**
     * Test OTA Emergency Number Database Update Status.
     */
    @Test
    public void testOtaEmergencyNumberDatabase() {
        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock);
        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("");
        processAllMessages();

        // Emergency Number Db is cached per country, given the country is empty at this time,
        // we should not expect any db number there.
        assertFalse(hasDbEmergencyNumber(CONFIG_EMERGENCY_NUMBER,
                mEmergencyNumberTrackerMock.getEmergencyNumberList()));

        // Set up the OTA database file folder as sdcard for testing purposes
        setOtaEmergencyNumberDbFileFolderForTesting(mEmergencyNumberTrackerMock, mPhone);
        // Notify EmergerncyNumberTracker OTA database is installed.
        sendOtaEmergencyNumberDb(mEmergencyNumberTrackerMock);
        processAllMessages();

        assertEquals(OTA_UNIT_TEST_EMERGENCY_NUMBER_DB_VERSION,
                mEmergencyNumberTrackerMock.getEmergencyNumberDbVersion());

        // Emergency Number Db is cached per country, given the country is empty at this time,
        // we should not expect any db number there.
        assertFalse(hasDbEmergencyNumber(OTA_EMERGENCY_NUMBER_ADDRESS,
                mEmergencyNumberTrackerMock.getEmergencyNumberList()));

        mEmergencyNumberTrackerMock.updateEmergencyCountryIsoAllPhones("us");
        processAllMessages();
        assertEquals(OTA_UNIT_TEST_EMERGENCY_NUMBER_DB_VERSION,
                mEmergencyNumberTrackerMock.getEmergencyNumberDbVersion());

        // Emergency Number Db is cached per country, given the country is 'us' at this time,
        // we should expect the 'us' db number there.
        assertTrue(hasDbEmergencyNumber(OTA_EMERGENCY_NUMBER_ADDRESS,
                mEmergencyNumberTrackerMock.getEmergencyNumberList()));

        // Reset the OTA database file to default after testing completion
        resetOtaEmergencyNumberDbFileFolderForTesting(mEmergencyNumberTrackerMock);
    }

    @Test
    public void testEmergencyNumberListPrefix() throws Exception {
        sendEmergencyNumberListFromRadio();
        sendEmergencyNumberPrefix(mEmergencyNumberTrackerMock);
        List<EmergencyNumber> resultToVerify = mEmergencyNumberListTestSample;
        resultToVerify.add(new EmergencyNumber("123119", "jp", "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN));
        resultToVerify.add(new EmergencyNumber("456119", "jp", "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN));
        Collections.sort(resultToVerify);

        List<EmergencyNumber> resultFromRadio = mEmergencyNumberTrackerMock
                .getRadioEmergencyNumberList();
        Collections.sort(resultFromRadio);

        assertEquals(resultToVerify, resultFromRadio);
    }

    @Test
    public void testOverridingEmergencyNumberPrefixCarrierConfig() throws Exception {
        // Capture CarrierConfigChangeListener to emulate the carrier config change notification
        doReturn(mMockContext).when(mPhone).getContext();
        doReturn(Context.CARRIER_CONFIG_SERVICE)
                .when(mMockContext)
                .getSystemService(CarrierConfigManager.class);
        doReturn(mCarrierConfigManagerMock)
                .when(mMockContext)
                .getSystemService(eq(Context.CARRIER_CONFIG_SERVICE));
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        EmergencyNumberTracker localEmergencyNumberTracker =
                new EmergencyNumberTracker(mPhone, mSimulatedCommands);
        verify(mCarrierConfigManagerMock)
                .registerCarrierConfigChangeListener(any(), listenerArgumentCaptor.capture());
        CarrierConfigManager.CarrierConfigChangeListener carrierConfigChangeListener =
                listenerArgumentCaptor.getAllValues().get(0);

        assertFalse(localEmergencyNumberTracker.isEmergencyNumber("*272911"));

        PersistableBundle bundle = new PersistableBundle();
        bundle.putStringArray(
                CarrierConfigManager.KEY_EMERGENCY_NUMBER_PREFIX_STRING_ARRAY,
                new String[] {"*272"});
        doReturn(bundle)
                .when(mCarrierConfigManagerMock)
                .getConfigForSubId(eq(SUB_ID_PHONE_1), any());
        carrierConfigChangeListener.onCarrierConfigChanged(
                mPhone.getPhoneId(),
                mPhone.getSubId(),
                TelephonyManager.UNKNOWN_CARRIER_ID,
                TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();

        assertTrue(localEmergencyNumberTracker.isEmergencyNumber("*272911"));
    }
}
