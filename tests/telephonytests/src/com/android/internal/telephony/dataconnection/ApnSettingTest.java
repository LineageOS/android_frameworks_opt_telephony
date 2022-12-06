/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.List;

public class ApnSettingTest extends TelephonyTest {

    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
    }

    @After
    public void tearDown() throws Exception {
        mBundle = null;
        super.tearDown();
    }

    static ApnSetting createApnSetting(int apnTypesBitmask) {
        return createApnSettingInternal(apnTypesBitmask, true);
    }

    private static ApnSetting createDisabledApnSetting(int apnTypesBitmask) {
        return createApnSettingInternal(apnTypesBitmask, false);
    }

    private static ApnSetting createApnSettingInternal(int apnTypeBitmask, boolean carrierEnabled) {
        return new ApnSetting.Builder()
                .setId(2163)
                .setOperatorNumeric("44010")
                .setEntryName("sp-mode")
                .setApnName("fake_apn")
                .setApnTypeBitmask(apnTypeBitmask)
                .setProtocol(ApnSetting.PROTOCOL_IP)
                .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                .setCarrierEnabled(carrierEnabled)
                .build();
    }

    private static void assertApnSettingsEqual(List<ApnSetting> a1, List<ApnSetting> a2) {
        assertEquals(a1.size(), a2.size());
        for (int i = 0; i < a1.size(); ++i) {
            assertApnSettingEqual(a1.get(i), a2.get(i));
        }
    }

    private static void assertApnSettingEqual(ApnSetting a1, ApnSetting a2) {
        assertEquals(a1.getEntryName(), a2.getEntryName());
        assertEquals(a1.getApnName(), a2.getApnName());
        assertEquals(a1.getProxyAddressAsString(), a2.getProxyAddressAsString());
        assertEquals(a1.getProxyPort(), a2.getProxyPort());
        assertEquals(a1.getMmsc(), a2.getMmsc());
        assertEquals(a1.getMmsProxyAddressAsString(), a2.getMmsProxyAddressAsString());
        assertEquals(a1.getMmsProxyPort(), a2.getMmsProxyPort());
        assertEquals(a1.getUser(), a2.getUser());
        assertEquals(a1.getPassword(), a2.getPassword());
        assertEquals(a1.getAuthType(), a2.getAuthType());
        assertEquals(a1.getId(), a2.getId());
        assertEquals(a1.getOperatorNumeric(), a2.getOperatorNumeric());
        assertEquals(a1.getProtocol(), a2.getProtocol());
        assertEquals(a1.getRoamingProtocol(), a2.getRoamingProtocol());
        assertEquals(a1.getApnTypeBitmask(), a2.getApnTypeBitmask());
        assertEquals(a1.isEnabled(), a2.isEnabled());
        assertEquals(a1.getProfileId(), a2.getProfileId());
        assertEquals(a1.isPersistent(), a2.isPersistent());
        assertEquals(a1.getMaxConns(), a2.getMaxConns());
        assertEquals(a1.getWaitTime(), a2.getWaitTime());
        assertEquals(a1.getMaxConnsTime(), a2.getMaxConnsTime());
        assertEquals(a1.getMtuV4(), a2.getMtuV4());
        assertEquals(a1.getMvnoType(), a2.getMvnoType());
        assertEquals(a1.getMvnoMatchData(), a2.getMvnoMatchData());
        assertEquals(a1.getNetworkTypeBitmask(), a2.getNetworkTypeBitmask());
        assertEquals(a1.getApnSetId(), a2.getApnSetId());
        assertEquals(a1.getSkip464Xlat(), a2.getSkip464Xlat());
    }

    @Test
    @SmallTest
    public void testIsMetered() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(1).when(mPhone).getSubId();

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_DEFAULT), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_MMS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_MMS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_DUN), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_ALL), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_FOTA), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_IA | ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_DEFAULT, mPhone));
        assertTrue(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_MMS, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_SUPL, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_CBS, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_DUN, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_FOTA, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_IA, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_HIPRI, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_XCAP, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_ENTERPRISE, mPhone));

        // Carrier config settings changes.
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING});

        assertTrue(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_DEFAULT, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_MMS, mPhone));
    }

    @Test
    @SmallTest
    public void testIsRoamingMetered() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(1).when(mPhone).getSubId();

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_DEFAULT), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_MMS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_MMS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_DUN), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_ALL), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_FOTA), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_IA | ApnSetting.TYPE_CBS), mPhone));

        // Carrier config settings changes.
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_FOTA_STRING});

        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_DEFAULT, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_MMS, mPhone));
        assertTrue(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_FOTA, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_XCAP, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_ENTERPRISE, mPhone));
    }

    @Test
    @SmallTest
    public void testIsMeteredAnother() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_SUPL_STRING, ApnSetting.TYPE_CBS_STRING});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(1).when(mPhone).getSubId();

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_SUPL), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_FOTA | ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_IA), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_ALL), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_IMS), mPhone));

        assertFalse(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_IMS), mPhone));
        assertFalse(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_XCAP), mPhone));
        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_ENTERPRISE), mPhone));
    }

    @Test
    @SmallTest
    public void testIsRoamingMeteredAnother() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{ApnSetting.TYPE_SUPL_STRING, ApnSetting.TYPE_CBS_STRING});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(2).when(mPhone).getSubId();

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_SUPL), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_FOTA | ApnSetting.TYPE_CBS), mPhone));

        assertTrue(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_SUPL | ApnSetting.TYPE_IA), mPhone));

        assertTrue(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_ALL), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_IMS), mPhone));

        assertFalse(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_IMS), mPhone));

        assertTrue(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_SUPL, mPhone));
        assertTrue(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_CBS, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_DEFAULT, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_MMS, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_DUN, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_FOTA, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_IA, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_HIPRI, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_XCAP, mPhone));
        assertFalse(ApnSettingUtils.isMeteredApnType(ApnSetting.TYPE_ENTERPRISE, mPhone));
    }

    @Test
    @SmallTest
    public void testIsMeteredNothingCharged() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(3).when(mPhone).getSubId();

        assertFalse(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_IMS), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_FOTA), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_ALL), mPhone));
    }

    @Test
    @SmallTest
    public void testIsRoamingMeteredNothingCharged() {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(3).when(mPhone).getSubId();

        assertFalse(ApnSettingUtils.isMetered(createApnSetting(ApnSetting.TYPE_IMS), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_FOTA), mPhone));

        assertFalse(ApnSettingUtils.isMetered(
                createApnSetting(ApnSetting.TYPE_ALL), mPhone));
    }

    @Test
    @SmallTest
    public void testCanHandleType() {
        String types[] = {"mms"};

        assertTrue(createApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_MMS));

        assertFalse(createApnSetting(ApnSetting.TYPE_DEFAULT)
                .canHandleType(ApnSetting.TYPE_MMS));

        assertTrue(createApnSetting(ApnSetting.TYPE_DEFAULT)
                .canHandleType(ApnSetting.TYPE_DEFAULT));

        // Hipri is asymmetric
        assertTrue(createApnSetting(ApnSetting.TYPE_DEFAULT)
                .canHandleType(ApnSetting.TYPE_HIPRI));
        assertFalse(createApnSetting(ApnSetting.TYPE_HIPRI)
                .canHandleType(ApnSetting.TYPE_DEFAULT));


        assertTrue(createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                .canHandleType(ApnSetting.TYPE_DEFAULT));

        assertTrue(createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                .canHandleType(ApnSetting.TYPE_MMS));

        assertFalse(createApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                .canHandleType(ApnSetting.TYPE_SUPL));

        // special IA case - doesn't match wildcards
        assertFalse(createApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_IA));
        assertTrue(createApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_IA)
                .canHandleType(ApnSetting.TYPE_IA));

        // same for emergency, mcx, xcap, and enterprise
        assertFalse(createApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_EMERGENCY));
        assertTrue(createApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_EMERGENCY)
                .canHandleType(ApnSetting.TYPE_EMERGENCY));
        assertFalse(createApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_MCX));
        assertTrue(createApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_MCX)
                .canHandleType(ApnSetting.TYPE_MCX));
        assertFalse(createApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_XCAP));
        assertTrue(createApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_XCAP)
                .canHandleType(ApnSetting.TYPE_XCAP));
        assertFalse(createApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_ENTERPRISE));
        assertTrue(createApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_ENTERPRISE)
                .canHandleType(ApnSetting.TYPE_ENTERPRISE));

        // check carrier disabled
        assertFalse(createDisabledApnSetting(ApnSetting.TYPE_ALL)
                .canHandleType(ApnSetting.TYPE_MMS));
        assertFalse(createDisabledApnSetting(ApnSetting.TYPE_DEFAULT)
                .canHandleType(ApnSetting.TYPE_DEFAULT));
        assertFalse(createDisabledApnSetting(ApnSetting.TYPE_DEFAULT)
                .canHandleType(ApnSetting.TYPE_HIPRI));
        assertFalse(createDisabledApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                .canHandleType(ApnSetting.TYPE_DEFAULT));
        assertFalse(createDisabledApnSetting(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                .canHandleType(ApnSetting.TYPE_MMS));
        assertFalse(createDisabledApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_IA)
                .canHandleType(ApnSetting.TYPE_IA));
        assertFalse(createDisabledApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_XCAP)
                .canHandleType(ApnSetting.TYPE_XCAP));
        assertFalse(createDisabledApnSetting(
                ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS | ApnSetting.TYPE_ENTERPRISE)
                .canHandleType(ApnSetting.TYPE_ENTERPRISE));
    }

    @Test
    @SmallTest
    public void testEquals() throws Exception {
        final int dummyInt = 1;
        final int dummyLong = 1;
        final String dummyString = "dummy";
        final String[] dummyStringArr = new String[] {"dummy"};
        final InetAddress dummyProxyAddress = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
        final Uri dummyUri = Uri.parse("www.google.com");
        // base apn
        ApnSetting baseApn = createApnSetting(ApnSetting.TYPE_MMS | ApnSetting.TYPE_DEFAULT);
        Field[] fields = ApnSetting.class.getDeclaredFields();
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            f.setAccessible(true);
            ApnSetting testApn = null;
            if (int.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.setInt(testApn, dummyInt + f.getInt(testApn));
            } else if (long.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.setLong(testApn, dummyLong + f.getLong(testApn));
            } else if (boolean.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.setBoolean(testApn, !f.getBoolean(testApn));
            } else if (String.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyString);
            } else if (String[].class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyStringArr);
            } else if (InetAddress.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyProxyAddress);
            } else if (Uri.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyUri);
            } else {
                fail("Unsupported field:" + f.getName());
            }
            if (testApn != null) {
                assertFalse(f.getName() + " is NOT checked", testApn.equals(baseApn));
            }
        }
    }

    @Test
    @SmallTest
    public void testEqualsRoamingProtocol() {
        ApnSetting apn1 = new ApnSetting.Builder()
                .setId(1234)
                .setOperatorNumeric("310260")
                .setEntryName("ims")
                .setApnName("ims")
                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask(
                        ServiceState.convertBearerBitmaskToNetworkTypeBitmask(131071))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .build();

        ApnSetting apn2 = new ApnSetting.Builder()
                .setId(1235)
                .setOperatorNumeric("310260")
                .setEntryName("ims")
                .setApnName("ims")
                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setRoamingProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask(
                        ServiceState.convertBearerBitmaskToNetworkTypeBitmask(131072))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .build();

        assertTrue(apn1.equals(apn2, false));
        assertFalse(apn1.equals(apn2, true));
    }

    @Test
    @SmallTest
    public void testCanHandleNetwork() {
        ApnSetting apn1 = new ApnSetting.Builder()
                .setId(1234)
                .setOperatorNumeric("310260")
                .setEntryName("ims")
                .setApnName("ims")
                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .build();

        ApnSetting apn2 = new ApnSetting.Builder()
                .setId(1235)
                .setOperatorNumeric("310260")
                .setEntryName("ims")
                .setApnName("ims")
                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setRoamingProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .build();

        assertFalse(apn1.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_1xRTT));
        assertTrue(apn1.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_LTE));
        assertTrue(apn1.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_UMTS));

        assertFalse(apn2.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_1xRTT));
        assertFalse(apn2.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_LTE));
        assertTrue(apn2.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_GPRS));
        assertTrue(apn2.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_EDGE));

        assertTrue(apn2.canSupportNetworkType(TelephonyManager.NETWORK_TYPE_GSM));
    }

    @Test
    public void testParcel() {
        ApnSetting apn = createApnSetting(ApnSetting.TYPE_DEFAULT);

        Parcel parcel = Parcel.obtain();
        apn.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);

        ApnSetting fromParcel = ApnSetting.CREATOR.createFromParcel(parcel);

        assertEquals(apn, fromParcel);

        parcel.recycle();
    }

    @Test
    public void testBuild_mmsProxyAddrStartsWithHttp() {
        ApnSetting apn1 = new ApnSetting.Builder()
                .setId(1234)
                .setOperatorNumeric("310260")
                .setEntryName("mms")
                .setApnName("mms")
                .setApnTypeBitmask(ApnSetting.TYPE_MMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .setMmsProxyAddress("http://proxy.mobile.att.net")
                .build();
        assertEquals("proxy.mobile.att.net", apn1.getMmsProxyAddressAsString());

        ApnSetting apn2 = new ApnSetting.Builder()
                .setId(1235)
                .setOperatorNumeric("310260")
                .setEntryName("mms")
                .setApnName("mms")
                .setApnTypeBitmask(ApnSetting.TYPE_MMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setRoamingProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .setMmsProxyAddress("https://proxy.mobile.att.net")
                .build();
        assertEquals("proxy.mobile.att.net", apn2.getMmsProxyAddressAsString());

        ApnSetting apn3 = new ApnSetting.Builder()
                .setId(1236)
                .setOperatorNumeric("310260")
                .setEntryName("mms")
                .setApnName("mms")
                .setApnTypeBitmask(ApnSetting.TYPE_MMS)
                .setProtocol(ApnSetting.PROTOCOL_IPV6)
                .setRoamingProtocol(ApnSetting.PROTOCOL_IPV6)
                .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS))
                .setMtuV4(1440)
                .setCarrierEnabled(true)
                .setMmsProxyAddress("proxy.mobile.att.net")
                .build();
        assertEquals("proxy.mobile.att.net", apn3.getMmsProxyAddressAsString());
    }
}
