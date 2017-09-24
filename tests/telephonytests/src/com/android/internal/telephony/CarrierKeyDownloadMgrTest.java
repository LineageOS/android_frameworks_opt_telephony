/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ImsiEncryptionInfo;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.MockitoAnnotations;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CarrierKeyDownloadMgrTest extends TelephonyTest {

    private CarrierKeyDownloadManager mCarrierKeyDM;
    private CarrierActionAgentHandler mCarrierActionAgentHandler;

    private String mURL = "http://www.google.com";

    private String mJsonStr = "{ \"carrier-keys\": [ { \"key\": \"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCjGGHATBYlmas+0sEECkno8LZ1KPglb/mfe6VpCT3GhSr+7br7NG/ZwGZnEhLqE7YIH4fxltHmQC3Tz+jM1YN+kMaQgRRjo/LBCJdOKaMwUbkVynAH6OYsKevjrOPk8lfM5SFQzJMGsA9+Tfopr5xg0BwZ1vA/+E3mE7Tr3M2UvwIDAQAB\", \"type\": \"WLAN\", \"identifier\": \"key1=value\", \"expiration-date\": 1502577746000 }, { \"key\": \"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCjGGHATBYlmas+0sEECkno8LZ1KPglb/mfe6VpCT3GhSr+7br7NG/ZwGZnEhLqE7YIH4fxltHmQC3Tz+jM1YN+kMaQgRRjo/LBCJdOKaMwUbkVynAH6OYsKevjrOPk8lfM5SFQzJMGsA9+Tfopr5xg0BwZ1vA/+E3mE7Tr3M2UvwIDAQAB\", \"type\": \"WLAN\", \"identifier\": \"key1=value\", \"expiration-date\": 1502577746000 }]}";

    private class CarrierActionAgentHandler extends HandlerThread {

        private CarrierActionAgentHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mCarrierKeyDM = new CarrierKeyDownloadManager(mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("CarrierActionAgentTest +Setup!");
        MockitoAnnotations.initMocks(this);
        super.setUp(getClass().getSimpleName());
        mCarrierActionAgentHandler = new CarrierActionAgentHandler(getClass().getSimpleName());
        mCarrierActionAgentHandler.start();
        waitUntilReady();
        logd("CarrierActionAgentTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mCarrierActionAgentHandler.quit();
        super.tearDown();
    }

    /* Checks if the expiration date is calculated correctly
     * In this case the expiration date should be the next day.
     */
    @Test
    @SmallTest
    public void testExpirationDate1Day() {
        java.security.PublicKey publicKey = null;
        mCarrierKeyDM.mKeyAvailability = 3;
        SimpleDateFormat dt = new SimpleDateFormat("yyyy-mm-dd");
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 6);
        Date date = cal.getTime();
        Calendar expectedCal = new GregorianCalendar();
        expectedCal.add(Calendar.DATE, 1);
        String dateExpected = dt.format(expectedCal.getTime());
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("mcc", "mnc", 1,
                "keyIdentifier", publicKey, date);
        when(mPhone.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(imsiEncryptionInfo);
        Date expirationDate = new Date(mCarrierKeyDM.getExpirationDate());
        assertTrue(dt.format(expirationDate).equals(dateExpected));
    }

    /**
     * Checks if the expiration date is calculated correctly
     * In this case the expiration date should be the expiration date of the key.
     **/
    @Test
    @SmallTest
    public void testExpirationDate7Day() {
        java.security.PublicKey publicKey = null;
        mCarrierKeyDM.mKeyAvailability = 3;
        SimpleDateFormat dt = new SimpleDateFormat("yyyy-mm-dd");
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 10);
        Date date = cal.getTime();
        Calendar expectedCal = new GregorianCalendar();
        expectedCal.add(Calendar.DATE, 3);
        String dateExpected = dt.format(expectedCal.getTime());
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("mcc", "mnc", 1,
                "keyIdentifier", publicKey, date);
        when(mPhone.getCarrierInfoForImsiEncryption(anyInt())).thenReturn(imsiEncryptionInfo);
        Date expirationDate = new Date(mCarrierKeyDM.getExpirationDate());
        assertTrue(dt.format(expirationDate).equals(dateExpected));
    }

    /**
     * Checks if the json is parse correctly.
     * Verify if the savePublicKey method is called with the right params.
     **/
    @Test
    @SmallTest
    public void testParseJson() {
        Date expirationDate = new Date(1502577746000L);
        String key = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCjGGHATBYlmas+0sEECkno8LZ1KPglb/mfe6VpCT3GhSr+7br7NG/ZwGZnEhLqE7YIH4fxltHmQC3Tz+jM1YN+kMaQgRRjo/LBCJdOKaMwUbkVynAH6OYsKevjrOPk8lfM5SFQzJMGsA9+Tfopr5xg0BwZ1vA/+E3mE7Tr3M2UvwIDAQAB";
        byte[] keyBytes = Base64.decode(key.getBytes(), Base64.DEFAULT);
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("310", "270", 2,
                "key1=value", keyBytes, expirationDate);
        String mccMnc = "310:270";
        mCarrierKeyDM.parseJsonAndPersistKey(mJsonStr, mccMnc);
        verify(mPhone, times(2)).setCarrierInfoForImsiEncryption((Matchers.refEq(imsiEncryptionInfo)));
    }

    /**
     * Checks if the json is parse correctly.
     * Since the json is bad, we want to verify that savePublicKey is not called.
     **/
    @Test
    @SmallTest
    public void testParseBadJsonFail() {
        String mccMnc = "310:290";
        String badJsonStr = "{badJsonString}";
        mCarrierKeyDM.parseJsonAndPersistKey(badJsonStr, mccMnc);
        verify(mPhone, times(0)).setCarrierInfoForImsiEncryption(any());
    }

    /**
     * Checks if the download is valid.
     * returns true since the mnc/mcc is valid.
     **/
    @Test
    @SmallTest
    public void testIsValidDownload() {
        String mccMnc = "310:260";
        when(mTelephonyManager.getNetworkOperator(anyInt())).thenReturn("310260");
        assertTrue(mCarrierKeyDM.isValidDownload(mccMnc));
    }

    /**
     * Checks if the download is valid.
     * returns false since the mnc/mcc is in-valid.
     **/
    @Test
    @SmallTest
    public void testIsValidDownloadFail() {
        String mccMnc = "310:290";
        when(mTelephonyManager.getNetworkOperator(anyInt())).thenReturn("310260");
        assertFalse(mCarrierKeyDM.isValidDownload(mccMnc));
    }

    /**
     * Tests if the key is enabled.
     * tests for all bit-mask value.
     **/
    @Test
    @SmallTest
    public void testIsKeyEnabled() {
        mCarrierKeyDM.mKeyAvailability = 3;
        assertTrue(mCarrierKeyDM.isKeyEnabled(1));
        assertTrue(mCarrierKeyDM.isKeyEnabled(2));
        mCarrierKeyDM.mKeyAvailability = 2;
        assertFalse(mCarrierKeyDM.isKeyEnabled(1));
        assertTrue(mCarrierKeyDM.isKeyEnabled(2));
        mCarrierKeyDM.mKeyAvailability = 1;
        assertTrue(mCarrierKeyDM.isKeyEnabled(1));
        assertFalse(mCarrierKeyDM.isKeyEnabled(2));
    }

    /**
     * Tests sending the ACTION_DOWNLOAD_COMPLETE intent.
     * Verify that the alarm will kick-off the next day.
     **/
    @Test
    @SmallTest
    public void testDownloadComplete() {
        SharedPreferences.Editor editor = getDefaultSharedPreferences(mContext).edit();
        String mccMnc = "310:260";
        int slotId = mPhone.getPhoneId();
        editor.putString("CARRIER_KEY_DM_MCC_MNC" + slotId, mccMnc);
        editor.commit();

        SimpleDateFormat dt = new SimpleDateFormat("yyyy-mm-dd");
        Calendar expectedCal = new GregorianCalendar();
        expectedCal.add(Calendar.DATE, 1);
        String dateExpected = dt.format(expectedCal.getTime());

        when(mTelephonyManager.getNetworkOperator(anyInt())).thenReturn("310260");
        Intent mIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        mContext.sendBroadcast(mIntent);
        Date expirationDate = new Date(mCarrierKeyDM.getExpirationDate());
        assertTrue(dt.format(expirationDate).equals(dateExpected));
    }

    /**
     * Test sending the ACTION_CARRIER_CONFIG_CHANGED intent.
     * Verify that the right mnc/mcc gets stored in the preferences.
     **/
    @Test
    @SmallTest
    public void testCarrierConfigChanged() {
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotId = mPhone.getPhoneId();
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotId);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, mURL);

        when(mTelephonyManager.getNetworkOperator(anyInt())).thenReturn("310260");
        Intent mIntent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 0);
        mContext.sendBroadcast(mIntent);
        SharedPreferences preferences = getDefaultSharedPreferences(mContext);
        String mccMnc = preferences.getString("CARRIER_KEY_DM_MCC_MNC" + slotId, null);
        assertTrue(mccMnc.equals("310:260"));
    }

    /**
     * Tests sending the INTENT_KEY_RENEWAL_ALARM_PREFIX intent.
     * Verify that the right mnc/mcc gets stored in the preferences.
     **/
    @Test
    @SmallTest
    public void testAlarmRenewal() {
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotId = mPhone.getPhoneId();
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotId);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, mURL);

        when(mTelephonyManager.getNetworkOperator(anyInt())).thenReturn("310260");
        Intent mIntent = new Intent("com.android.internal.telephony.carrier_key_download_alarm"
                + slotId);
        mContext.sendBroadcast(mIntent);
        SharedPreferences preferences = getDefaultSharedPreferences(mContext);
        String mccMnc = preferences.getString("CARRIER_KEY_DM_MCC_MNC" + slotId, null);
        assertTrue(mccMnc.equals("310:260"));
    }
}
