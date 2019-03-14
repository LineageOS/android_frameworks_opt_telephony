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

import androidx.test.runner.AndroidJUnit4;

import static com.google.common.truth.Truth.assertThat;

import android.telephony.ServiceState;

import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarrierDisplayNameResolverTest {

    private static final String PLMN_1 = "310260";
    private static final String PLMN_2 = "480123";
    private static final String PLMN_3 = "586111";
    private static final String HOME_PLMN_NUMERIC = PLMN_1;
    private static final String NON_HOME_PLMN_NUMERIC = "123456";
    private static final String SIM_SERVICE_PROVIDER_NAME = "spn";

    // Display SPN in home network, PLMN in roaming network.
    private static final int SIM_SPN_DISPLAY_CONDITION = 0;

    private static final boolean ON_ROAMING = true;
    private static final boolean OFF_ROAMING = false;
    private static final List<String> SIM_SPDI = Arrays.asList(PLMN_1, PLMN_2);
    private static final List<PlmnNetworkName> SIM_PNN_LIST =
            Arrays.asList(
                    new PlmnNetworkName("fullname1", "shortname1"),
                    new PlmnNetworkName("fullname2", "shortname2"),
                    new PlmnNetworkName("fullname3", "shortname3"));
    private static final List<OperatorPlmnInfo> SIM_OPERATOR_PLMN_INFO_LIST =
            Arrays.asList(
                    new OperatorPlmnInfo(PLMN_1, 100, 200, 0),
                    new OperatorPlmnInfo(PLMN_2, 300, 400, 1),
                    new OperatorPlmnInfo(PLMN_3, 400, 500, 2));

    private final CarrierDisplayNameResolver mCDNR = new CarrierDisplayNameResolverImpl();
    private final ServiceState mServiceState = new ServiceState();

    @Before
    public void setUp() {
        setDefaultValueAndState();
    }

    @Test
    public void testUpdateSPNFromHigherPrioritySource_shouldOverrideRecord() {
        // carrier config source > sim record source
        final String spnFromCarrierConfig = "spn from carrier config";
        mCDNR.updateServiceProviderName(
                CarrierDisplayNameResolver.EF_SOURCE_CARRIER_CONFIG, spnFromCarrierConfig);

        assertThat(mCDNR.getServiceProviderName()).isEqualTo(spnFromCarrierConfig);
    }

    @Test
    public void testUpdateSPNFromLowerPrioritySource_shouldNotOverrideRecord() {
        // CSIM source < sim record source
        final String spnFromCSIM = "spn from CSIM";
        mCDNR.updateServiceProviderName(CarrierDisplayNameResolver.EF_SOURCE_CSIM, spnFromCSIM);

        assertThat(mCDNR.getServiceProviderName()).isEqualTo(SIM_SERVICE_PROVIDER_NAME);
    }

    @Test
    public void testShouldShowSPN_offRoaming_showSPN() {
        mServiceState.setRoaming(OFF_ROAMING);
        mCDNR.updateServiceState(mServiceState);

        assertThat(mCDNR.shouldShowServiceProviderName()).isTrue();
    }

    @Test
    public void testShouldShowSPN_plmnNotInProvidedList_notShowSPN() {
        mServiceState.setRoaming(ON_ROAMING);
        mServiceState.setOperatorName("long name", "short name", NON_HOME_PLMN_NUMERIC);
        mCDNR.updateServiceState(mServiceState);

        assertThat(mCDNR.shouldShowServiceProviderName()).isFalse();
    }

    @Test
    public void testShouldShowSPN_plmnInProvidedList_showSPN() {
        mServiceState.setOperatorName("long name", "short name", SIM_SPDI.get(0));
        mCDNR.updateServiceState(mServiceState);

        assertThat(mCDNR.shouldShowServiceProviderName()).isTrue();
    }

    @Test
    public void testShouldShowPLMNNetworkName_onRoaming_showPLMNNetworkName() {
        mServiceState.setRoaming(ON_ROAMING);
        mServiceState.setOperatorName("long name", "short name", NON_HOME_PLMN_NUMERIC);
        mCDNR.updateServiceState(mServiceState);

        assertThat(mCDNR.shouldShowPlmnNetworkName()).isTrue();
    }

    @Test
    public void testShouldShowPLMNNetworkName_plmnNotInProvidedList_showPLMNNetworkName() {
        mServiceState.setRoaming(ON_ROAMING);
        mServiceState.setOperatorName("long name", "short name", NON_HOME_PLMN_NUMERIC);
        mCDNR.updateServiceState(mServiceState);

        assertThat(mCDNR.shouldShowPlmnNetworkName()).isTrue();
    }

    @Test
    public void testGetPLMNNetworkName_oplNotPresent_returnTheFirstEntryOfPNNList() {
        // carrier config source > sim record source
        mCDNR.updateOperatorPlmnList(CarrierDisplayNameResolver.EF_SOURCE_CARRIER_CONFIG,
                Collections.EMPTY_LIST);

        // Set the roaming state to on roaming, we should show the plmn network name based on the
        // default settings.
        mServiceState.setRoaming(ON_ROAMING);
        mCDNR.updateServiceState(mServiceState);

        assertThat(mCDNR.getPlmnNetworkName()).isEqualTo(SIM_PNN_LIST.get(0).fullName);
    }

    private void setDefaultValueAndState() {
        mServiceState.setRoaming(false);
        mServiceState.setOperatorName("long name", "short name", HOME_PLMN_NUMERIC);
        mCDNR.updateServiceState(mServiceState);

        mCDNR.updateHomePlmnNumeric(HOME_PLMN_NUMERIC);
        mCDNR.updateOperatorPlmnList(
                CarrierDisplayNameResolver.EF_SOURCE_USIM, SIM_OPERATOR_PLMN_INFO_LIST);
        mCDNR.updatePlmnNetworkNameList(
                CarrierDisplayNameResolver.EF_SOURCE_USIM, SIM_PNN_LIST);
        mCDNR.updateServiceProviderName(
                CarrierDisplayNameResolver.EF_SOURCE_USIM, SIM_SERVICE_PROVIDER_NAME);
        mCDNR.updateServiceProviderNameDisplayCondition(
                CarrierDisplayNameResolver.EF_SOURCE_USIM, SIM_SPN_DISPLAY_CONDITION);
        mCDNR.updateServiceProviderDisplayInformation(
                CarrierDisplayNameResolver.EF_SOURCE_USIM, SIM_SPDI);
        mCDNR.updateServiceState(serviceStateWithRegisteredPLMN(HOME_PLMN_NUMERIC));
    }

    private static ServiceState serviceStateWithRegisteredPLMN(String plmnNumeric) {
        ServiceState ss = new ServiceState();
        ss.setOperatorName("long name", "short name", plmnNumeric);
        return ss;
    }
}
