/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.GERAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.IWLAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.NGRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_ACR;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_ALL;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BAIC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BAOC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BIC_ROAM;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BIL;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BOIC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_BOIC_EXHC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_IBS;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CB_OBS;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_ALL;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_ALL_CONDITONAL_FORWARDING;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFB;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFNRC;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFNRY;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CF_CFU;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CW;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIP;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIR;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIP;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIR;

import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAICr;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOICxH;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_ALL;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MO;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MT;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.SsDomainController.CB_FACILITY_ACR;
import static com.android.internal.telephony.SsDomainController.CB_FACILITY_BIL;
import static com.android.internal.telephony.SsDomainController.SS_CLIP;
import static com.android.internal.telephony.SsDomainController.SS_CLIR;
import static com.android.internal.telephony.SsDomainController.SS_COLP;
import static com.android.internal.telephony.SsDomainController.SS_COLR;
import static com.android.internal.telephony.SsDomainController.SS_CW;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.TextUtils;

import com.android.internal.telephony.imsphone.ImsPhoneMmiCode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SsDomainControllerTest extends TelephonyTest {
    private static final int[] UT_OVER_ALL = new int[] {
            NGRAN,
            EUTRAN,
            IWLAN,
            UTRAN,
            GERAN
    };

    private static final int[]  UT_OVER_LTE_WIFI = new int[] {
            EUTRAN,
            IWLAN
    };

    private Map<String, String> mFacilities  = new HashMap<String, String>() {{
            put(CB_FACILITY_BAOC, "33");
            put(CB_FACILITY_BAOIC, "331");
            put(CB_FACILITY_BAOICxH, "332");
            put(CB_FACILITY_BAIC, "35");
            put(CB_FACILITY_BAICr, "351");
            put(CB_FACILITY_BA_ALL, "330");
            put(CB_FACILITY_BA_MO, "333");
            put(CB_FACILITY_BA_MT, "353");
            put(CB_FACILITY_BIL, "156");
            put(CB_FACILITY_ACR, "157");
        }};

    private Map<Integer, String> mReasons  = new HashMap<Integer, String>() {{
            put(CF_REASON_ALL, "002");
            put(CF_REASON_UNCONDITIONAL, "21");
            put(CF_REASON_BUSY, "67");
            put(CF_REASON_NOT_REACHABLE, "62");
            put(CF_REASON_NO_REPLY, "61");
            put(CF_REASON_ALL_CONDITIONAL, "004");
        }};

    private Map<String, String> mServices  = new HashMap<String, String>() {{
            put(SS_CW, "43");
            put(SS_CLIP, "30");
            put(SS_CLIR, "31");
            put(SS_COLP, "76");
            put(SS_COLR, "77");
        }};

    private SsDomainController mSdc;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        mSdc = new SsDomainController(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        mSdc = null;
        super.tearDown();
    }

    private void verifyCb(String facility) {
        for (String f : mFacilities.keySet()) {
            String sc = mFacilities.get(f);

            SsDomainController.SuppServiceRoutingInfo ssCode =
                    ImsPhoneMmiCode.getSuppServiceRoutingInfo("*#" + sc + "#", mSdc);
            assertNotNull(f + ", only " + facility + " available", ssCode);
            if (TextUtils.equals(f, facility)) {
                assertTrue(f + ", only " + facility + " available", mSdc.useCbOverUt(f));
                assertTrue(f + ", only " + facility + " available", ssCode.useSsOverUt());
            } else {
                assertFalse(f + ", only " + facility + " available", mSdc.useCbOverUt(f));
                assertFalse(f + ", only " + facility + " available", ssCode.useSsOverUt());
            }
        }
    }

    @Test
    @SmallTest
    public void testUseCbOverUt() {
        setUtEnabled();
        updateCarrierConfig(new int[] {});

        verifyCb("");

        /** barring_of_all_outgoing_calls (BAOC) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_BAOC });

        verifyCb(CB_FACILITY_BAOC);

        /** barring_of_outgoing_international_calls (BOIC) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_BOIC });

        verifyCb(CB_FACILITY_BAOIC);

        /** barring_of_outgoing_international_calls_except_to_home_plmn (BoICExHC) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_BOIC_EXHC });

        verifyCb(CB_FACILITY_BAOICxH);

        /** barring of all incoming calls (BAIC) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_BAIC });

        verifyCb(CB_FACILITY_BAIC);

        /** barring of incoming calls when roaming outside home PLMN Country (BICRoam) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_BIC_ROAM });

        verifyCb(CB_FACILITY_BAICr);

        /** barring list of incoming numbers (bil) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_BIL });

        verifyCb(CB_FACILITY_BIL);

        /** barring of all anonymous incoming number (acr) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_ACR });

        verifyCb(CB_FACILITY_ACR);

        /** all barring services(BA_ALL) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_ALL });

        verifyCb(CB_FACILITY_BA_ALL);

        /** outgoing barring services(BA_MO) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_OBS });

        verifyCb(CB_FACILITY_BA_MO);

        /** incoming barring services(BA_MT) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CB_IBS });

        verifyCb(CB_FACILITY_BA_MT);
    }

    private void verifyCf(int reason) {
        for (Integer r : mReasons.keySet()) {
            String sc = mReasons.get(r);

            SsDomainController.SuppServiceRoutingInfo ssCode =
                    ImsPhoneMmiCode.getSuppServiceRoutingInfo("*#" + sc + "#", mSdc);
            assertNotNull(r + ", only " + reason + " available", ssCode);
            if (r == reason) {
                assertTrue(r + ", only " + reason + " available", mSdc.useCfOverUt(r));
                assertTrue(r + ", only " + reason + " available", ssCode.useSsOverUt());
            } else {
                assertFalse(r + ", only " + reason + " available", mSdc.useCfOverUt(r));
                assertFalse(r + ", only " + reason + " available", ssCode.useSsOverUt());
            }
        }
    }

    @Test
    @SmallTest
    public void testUseCfOverUt() {
        setUtEnabled();
        updateCarrierConfig(new int[] {});

        verifyCf(-1);

        /** all_call_forwarding (CFAll) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CF_ALL });

        verifyCf(CF_REASON_ALL);

        /** all_forwarding_unconditional (CFU) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CF_CFU });

        verifyCf(CF_REASON_UNCONDITIONAL);

        /** all_call_conditional_forwarding (allCondForwarding) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CF_ALL_CONDITONAL_FORWARDING });

        verifyCf(CF_REASON_ALL_CONDITIONAL);

        /** call_forwarding_on_mobile_subscriber_busy (CFB) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CF_CFB });

        verifyCf(CF_REASON_BUSY);

        /** call_forwarding_on_no_reply (CFNRY) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CF_CFNRY });

        verifyCf(CF_REASON_NO_REPLY);

        /** call_forwarding_on_mobile_subscriber_unreachable (CFNRC) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CF_CFNRC });

        verifyCf(CF_REASON_NOT_REACHABLE);
    }

    private void verifySs(String service) {
        for (String s : mServices.keySet()) {
            String sc = mServices.get(s);

            SsDomainController.SuppServiceRoutingInfo ssCode =
                    ImsPhoneMmiCode.getSuppServiceRoutingInfo("*#" + sc + "#", mSdc);
            assertNotNull(s + ", only " + service + " available", ssCode);
            if (TextUtils.equals(s, service)) {
                assertTrue(s + ", only " + service + " available", mSdc.useSsOverUt(s));
                assertTrue(s + ", only " + service + " available", ssCode.useSsOverUt());
            } else {
                assertFalse(s + ", only " + service + " available", mSdc.useSsOverUt(s));
                assertFalse(s + ", only " + service + " available", ssCode.useSsOverUt());
            }
        }
    }

    @Test
    @SmallTest
    public void testUseSsOverUt() {
        setUtEnabled();
        updateCarrierConfig(new int[] {});
        verifySs("");

        /** call waiting (CW) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_CW });

        verifySs(SS_CW);

        /** OIP (clip) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIP });

        verifySs(SS_CLIP);

        /** TIP (colp) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIP });

        verifySs(SS_COLP);

        /** TIR (colr) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIR });

        verifySs(SS_COLR);

        /** OIR (clir) */
        updateCarrierConfig(new int[] { SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIR });

        verifySs(SS_CLIR);
    }

    @Test
    @SmallTest
    public void testUtEnabled() {
        doReturn(0).when(mImsPhone).getSubId();
        mSdc.updateWifiForUt(false);

        ServiceState imsSs = new ServiceState();
        // IMS is not registered
        imsSs.setState(ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(imsSs).when(mImsPhone).getServiceState();

        doReturn(true).when(mImsPhone).isUtEnabled();
        doReturn(mImsPhone).when(mPhone).getImsPhone();

        ServiceState ss = new ServiceState();
        // IMS is not registered
        ss.setState(ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(ss).when(mPhone).getServiceState();

        // WWAN_LEGACY is registered
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .build();

        ss.addNetworkRegistrationInfo(nri);

        // IMS Registration is NOT required, enabled when roaming, transport = ALL
        updateCarrierConfig(false, true, UT_OVER_ALL);

        assertTrue(mSdc.isUtEnabled());

        // IMS Registration is NOT required, enabled when roaming, transport = LTE | WiFi
        updateCarrierConfig(false, true, UT_OVER_LTE_WIFI);

        // Ut is not available over 3G and 2G.
        assertFalse(mSdc.isUtEnabled());

        // Wi-Fi is connected
        mSdc.updateWifiForUt(true);

        // Ut is available over WiFi.
        assertTrue(mSdc.isUtEnabled());

        // IMS Registration is REQUIRED, enabled when roaming, transport = LTE | WiFi
        updateCarrierConfig(true, true, UT_OVER_LTE_WIFI);

        // IMS is not registered.
        assertFalse(mSdc.isUtEnabled());

        // IMS is registered
        imsSs.setState(ServiceState.STATE_IN_SERVICE);

        assertTrue(mSdc.isUtEnabled());
    }

    @Test
    @SmallTest
    public void testUtWhenRoaming() {
        doReturn(0).when(mImsPhone).getSubId();
        mSdc.updateWifiForUt(false);

        ServiceState imsSs = new ServiceState();
        // IMS is not registered
        imsSs.setState(ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(imsSs).when(mImsPhone).getServiceState();

        doReturn(true).when(mImsPhone).isUtEnabled();
        doReturn(mImsPhone).when(mPhone).getImsPhone();

        ServiceState ss = new ServiceState();
        // IMS is not registered
        ss.setState(ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(ss).when(mPhone).getServiceState();

        // WWAN_LEGACY is registered
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .build();

        ss.addNetworkRegistrationInfo(nri);

        // IMS Registration is NOT required, enabled when roaming, transport = ALL
        updateCarrierConfig(false, true, UT_OVER_ALL);

        assertTrue(mSdc.isUtEnabled());

        // IMS Registration is NOT required, disabled when roaming, transport = ALL
        updateCarrierConfig(false, false, UT_OVER_ALL);

        // Ut is not available when roaming
        assertFalse(mSdc.isUtEnabled());
    }

    @Test
    @SmallTest
    public void testOemHandlesTerminalBasedCallWaiting() {
        setUtEnabled();

        // Enable terminal-based call waiting
        mSdc.updateCarrierConfigForTest(true, true, false, true, true,
                new int[] {}, UT_OVER_ALL, new int[] { SUPPLEMENTARY_SERVICE_CW });
        String sc = mServices.get(SS_CW);

        mSdc.setOemHandlesTerminalBasedCallWaiting(false);
        SsDomainController.SuppServiceRoutingInfo ssCode =
                ImsPhoneMmiCode.getSuppServiceRoutingInfo("*#" + sc + "#", mSdc);
        assertNotNull(ssCode);
        assertFalse(ssCode.useSsOverUt());

        mSdc.setOemHandlesTerminalBasedCallWaiting(true);
        ssCode = ImsPhoneMmiCode.getSuppServiceRoutingInfo("*#" + sc + "#", mSdc);

        assertNotNull(ssCode);
        assertTrue(ssCode.useSsOverUt());
    }

    private void setUtEnabled() {
        doReturn(0).when(mImsPhone).getSubId();
        mSdc.updateWifiForUt(false);

        ServiceState imsSs = new ServiceState();
        // IMS is not registered
        imsSs.setState(ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(imsSs).when(mImsPhone).getServiceState();

        doReturn(true).when(mImsPhone).isUtEnabled();
        doReturn(mImsPhone).when(mPhone).getImsPhone();

        ServiceState ss = new ServiceState();
        // IMS is not registered
        ss.setState(ServiceState.STATE_OUT_OF_SERVICE);
        doReturn(ss).when(mPhone).getServiceState();

        // WWAN_LEGACY is registered
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .build();

        ss.addNetworkRegistrationInfo(nri);
    }

    private void updateCarrierConfig(boolean requiresImsRegistration,
            boolean availableWhenRoaming, int[] utRats) {
        updateCarrierConfig(true, requiresImsRegistration, true, availableWhenRoaming, utRats);
    }

    private void updateCarrierConfig(boolean supportsCsfb, boolean requiresImsRegistration,
            boolean availableWhenPsDataOff, boolean availableWhenRoaming, int[] utRats) {
        mSdc.updateCarrierConfigForTest(true, supportsCsfb, requiresImsRegistration,
                availableWhenPsDataOff, availableWhenRoaming, null, utRats, null);
    }

    private void updateCarrierConfig(int[] services) {
        mSdc.updateCarrierConfigForTest(true, true, false, true, true, services, UT_OVER_ALL, null);
    }
}
