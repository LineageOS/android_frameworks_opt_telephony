/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.telephony.AccessNetworkConstants;
import android.telephony.INetworkService;
import android.telephony.INetworkServiceCallback;
import android.telephony.LteVopsSupportInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.NrVopsSupportInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.VopsSupportInfo;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.internal.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CellularNetworkServiceTest extends TelephonyTest {
    CellularNetworkService mCellularNetworkService;

    @Mock
    private INetworkServiceCallback mCallback;

    private void addNetworkService() {
        mCellularNetworkService = new CellularNetworkService();
        ServiceInfo serviceInfo =  new ServiceInfo();
        serviceInfo.packageName = "com.android.phone";
        serviceInfo.permission = "android.permission.BIND_TELEPHONY_NETWORK_SERVICE";
        IntentFilter filter = new IntentFilter();
        mContextFixture.addService(
                NetworkService.SERVICE_INTERFACE,
                null,
                "com.android.phone",
                mCellularNetworkService.mBinder,
                serviceInfo,
                filter);
    }
    INetworkService.Stub mBinder;

    @Before
    public void setUp() throws Exception {

        logd("CellularNetworkServiceTest +Setup!");
        super.setUp("CellularNetworkServiceTest");

        mContextFixture.putResource(R.string.config_wwan_network_service_package,
                "com.android.phone");
        addNetworkService();
        mBinder = mCellularNetworkService.mBinder;
        mBinder.createNetworkServiceProvider(0);

        int dds = SubscriptionManager.getDefaultDataSubscriptionId();
        doReturn(dds).when(mPhone).getSubId();

        logd("CellularNetworkServiceTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        if (mCellularNetworkService != null) {
            mCellularNetworkService.onDestroy();
        }
        super.tearDown();
    }

    @Test
    @MediumTest
    public void testGetNetworkRegistrationInfo() {
        int voiceRegState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
        int dataRegState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
        int voiceRadioTech = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
        int dataRadioTech = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
        int domain = NetworkRegistrationInfo.DOMAIN_CS;

        boolean cssSupported = true;
        int roamingIndicator = 1;
        int systemIsInPrl = 2;
        int defaultRoamingIndicator = 3;
        int reasonForDenial = 0;
        int maxDataCalls = 4;
        List<Integer> availableServices = new ArrayList<>(Arrays.asList(new Integer[] {
                NetworkRegistrationInfo.SERVICE_TYPE_VOICE,
                NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                NetworkRegistrationInfo.SERVICE_TYPE_VIDEO
        }));

        mSimulatedCommands.setVoiceRegState(voiceRegState);
        mSimulatedCommands.setVoiceRadioTech(voiceRadioTech);
        mSimulatedCommands.setDataRegState(dataRegState);
        mSimulatedCommands.setDataRadioTech(dataRadioTech);
        mSimulatedCommands.mCssSupported = cssSupported;
        mSimulatedCommands.mRoamingIndicator = roamingIndicator;
        mSimulatedCommands.mSystemIsInPrl = systemIsInPrl;
        mSimulatedCommands.mDefaultRoamingIndicator = defaultRoamingIndicator;
        mSimulatedCommands.mReasonForDenial = reasonForDenial;
        mSimulatedCommands.mMaxDataCalls = maxDataCalls;

        mSimulatedCommands.notifyNetworkStateChanged();

        try {
            mBinder.requestNetworkRegistrationInfo(0, domain, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        NetworkRegistrationInfo expectedState = new NetworkRegistrationInfo(
                domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN, voiceRegState,
                ServiceState.rilRadioTechnologyToNetworkType(voiceRadioTech), reasonForDenial,
                false, availableServices, null, "", cssSupported,
                roamingIndicator, systemIsInPrl, defaultRoamingIndicator);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }

        domain = NetworkRegistrationInfo.DOMAIN_PS;
        availableServices = Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA);
        try {
            mBinder.requestNetworkRegistrationInfo(0, domain, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        VopsSupportInfo lteVopsSupportInfo =
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_AVAILABLE,
                        LteVopsSupportInfo.LTE_STATUS_NOT_AVAILABLE);

        expectedState = new NetworkRegistrationInfo(
                domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN, voiceRegState,
                ServiceState.rilRadioTechnologyToNetworkType(voiceRadioTech), reasonForDenial,
                false, availableServices, null, "", maxDataCalls, false, false, false,
                lteVopsSupportInfo);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }
    }

    @Test
    @MediumTest
    public void testGetNetworkRegistrationInfoV1_5() {
        // common parameters
        int regState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
        int radioTech = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
        int reasonForDenial = 0;

        // voice services
        List<Integer> availableVoiceServices = new ArrayList<>(Arrays.asList(new Integer[] {
                NetworkRegistrationInfo.SERVICE_TYPE_VOICE,
                NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                NetworkRegistrationInfo.SERVICE_TYPE_VIDEO
        }));

        // Default value per 24.008
        int maxDataCalls = 16;
        // data service
        List<Integer> availableDataServices = Arrays.asList(
                NetworkRegistrationInfo.SERVICE_TYPE_DATA);

        // ENDC parameters
        boolean isEndcAvailable = true;
        boolean isDcNrRestricted = false;
        boolean isNrAvailable = true;

        // LTE VoPS parameters
        boolean isVopsSupported = true;
        boolean isEmcBearerSupported = true;

        android.hardware.radio.V1_5.RegStateResult regResult =
                new android.hardware.radio.V1_5.RegStateResult();

        regResult.regState = regState;
        regResult.rat = radioTech;
        regResult.reasonForDenial = reasonForDenial;

        android.hardware.radio.V1_5.RegStateResult.AccessTechnologySpecificInfo
                .EutranRegistrationInfo eutranInfo = new android.hardware.radio.V1_5
                .RegStateResult.AccessTechnologySpecificInfo.EutranRegistrationInfo();
        eutranInfo.lteVopsInfo.isVopsSupported = isVopsSupported;
        eutranInfo.lteVopsInfo.isEmcBearerSupported = isEmcBearerSupported;
        eutranInfo.nrIndicators.isEndcAvailable = isEndcAvailable;
        eutranInfo.nrIndicators.isDcNrRestricted = isDcNrRestricted;
        eutranInfo.nrIndicators.isNrAvailable = isNrAvailable;
        regResult.accessTechnologySpecificInfo.eutranInfo(eutranInfo);

        VopsSupportInfo vops = new LteVopsSupportInfo(
                LteVopsSupportInfo.LTE_STATUS_SUPPORTED, LteVopsSupportInfo.LTE_STATUS_SUPPORTED);

        mSimulatedCommands.setVoiceRegStateResult(regResult);
        mSimulatedCommands.setDataRegStateResult(regResult);

        // test voice registration state
        try {
            mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_CS, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        NetworkRegistrationInfo expectedState = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                regState, ServiceState.rilRadioTechnologyToNetworkType(radioTech), reasonForDenial,
                false, availableVoiceServices, null, "", false, 0, 0, 0);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }

        // test data registration state
        try {
            mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        expectedState = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                regState, ServiceState.rilRadioTechnologyToNetworkType(radioTech), reasonForDenial,
                false, availableDataServices, null, "", maxDataCalls, isDcNrRestricted,
                isNrAvailable, isEndcAvailable, vops);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }
    }

    @Test
    @MediumTest
    public void testGetNetworkRegistrationInfoV1_6WithLte() {
        // common parameters
        int regState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
        int radioTech = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
        int reasonForDenial = 0;

        // voice services
        List<Integer> availableVoiceServices = new ArrayList<>(Arrays.asList(new Integer[] {
                NetworkRegistrationInfo.SERVICE_TYPE_VOICE,
                NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                NetworkRegistrationInfo.SERVICE_TYPE_VIDEO
        }));

        // Default value per 24.008
        int maxDataCalls = 16;
        // data service
        List<Integer> availableDataServices = Arrays.asList(
                NetworkRegistrationInfo.SERVICE_TYPE_DATA);

        // ENDC parameters
        boolean isEndcAvailable = true;
        boolean isDcNrRestricted = false;
        boolean isNrAvailable = true;

        // LTE VoPS parameters
        boolean isVopsSupported = true;
        boolean isEmcBearerSupported = true;

        android.hardware.radio.V1_6.RegStateResult regResult =
                new android.hardware.radio.V1_6.RegStateResult();

        regResult.regState = regState;
        regResult.rat = radioTech;
        regResult.reasonForDenial = reasonForDenial;

        android.hardware.radio.V1_5.RegStateResult.AccessTechnologySpecificInfo
                .EutranRegistrationInfo eutranInfo = new android.hardware.radio.V1_5
                .RegStateResult.AccessTechnologySpecificInfo.EutranRegistrationInfo();
        eutranInfo.lteVopsInfo.isVopsSupported = isVopsSupported;
        eutranInfo.lteVopsInfo.isEmcBearerSupported = isEmcBearerSupported;
        eutranInfo.nrIndicators.isEndcAvailable = isEndcAvailable;
        eutranInfo.nrIndicators.isDcNrRestricted = isDcNrRestricted;
        eutranInfo.nrIndicators.isNrAvailable = isNrAvailable;
        regResult.accessTechnologySpecificInfo.eutranInfo(eutranInfo);

        VopsSupportInfo vops = new LteVopsSupportInfo(
                LteVopsSupportInfo.LTE_STATUS_SUPPORTED, LteVopsSupportInfo.LTE_STATUS_SUPPORTED);

        mSimulatedCommands.setVoiceRegStateResult(regResult);
        mSimulatedCommands.setDataRegStateResult(regResult);

        // test voice registration state
        try {
            mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_CS, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        NetworkRegistrationInfo expectedState = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                regState, ServiceState.rilRadioTechnologyToNetworkType(radioTech), reasonForDenial,
                false, availableVoiceServices, null, "", false, 0, 0, 0);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }

        // test data registration state
        try {
            mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        expectedState = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                regState, ServiceState.rilRadioTechnologyToNetworkType(radioTech), reasonForDenial,
                false, availableDataServices, null, "", maxDataCalls, isDcNrRestricted,
                isNrAvailable, isEndcAvailable, vops);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }
    }


    @Test
    @MediumTest
    public void testGetNetworkRegistrationInfoV1_6WithNr() {
        // common parameters
        int regState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
        int radioTech = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
        int reasonForDenial = 0;

        // voice services
        List<Integer> availableVoiceServices = new ArrayList<>(Arrays.asList(new Integer[] {
                NetworkRegistrationInfo.SERVICE_TYPE_VOICE,
                NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                NetworkRegistrationInfo.SERVICE_TYPE_VIDEO
        }));

        // Default value per 24.008
        int maxDataCalls = 16;
        // data service
        List<Integer> availableDataServices = Arrays.asList(
                NetworkRegistrationInfo.SERVICE_TYPE_DATA);

        // NR VoPS parameters
        byte vopsSupported = android.hardware.radio.V1_6.VopsIndicator.VOPS_OVER_3GPP;
        byte emcSupported = android.hardware.radio.V1_6.EmcIndicator.EMC_NR_CONNECTED_TO_5GCN;
        byte emfSupported = android.hardware.radio.V1_6.EmfIndicator.EMF_NR_CONNECTED_TO_5GCN;

        android.hardware.radio.V1_6.RegStateResult regResult =
                new android.hardware.radio.V1_6.RegStateResult();

        regResult.regState = regState;
        regResult.rat = radioTech;
        regResult.reasonForDenial = reasonForDenial;

        regResult.accessTechnologySpecificInfo.ngranNrVopsInfo(new android.hardware.radio.V1_6
                .NrVopsInfo());
        regResult.accessTechnologySpecificInfo.ngranNrVopsInfo().vopsSupported = vopsSupported;
        regResult.accessTechnologySpecificInfo.ngranNrVopsInfo().emcSupported = emcSupported;
        regResult.accessTechnologySpecificInfo.ngranNrVopsInfo().emfSupported = emfSupported;
        regResult.accessTechnologySpecificInfo.ngranNrVopsInfo(
                regResult.accessTechnologySpecificInfo.ngranNrVopsInfo());

        VopsSupportInfo vops = new NrVopsSupportInfo(vopsSupported, emcSupported, emfSupported);
        mSimulatedCommands.setVoiceRegStateResult(regResult);
        mSimulatedCommands.setDataRegStateResult(regResult);

        // test voice registration state
        try {
            mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_CS, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        NetworkRegistrationInfo expectedState = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                regState, ServiceState.rilRadioTechnologyToNetworkType(radioTech), reasonForDenial,
                false, availableVoiceServices, null, "", false, 0, 0, 0);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }

        // test data registration state
        try {
            mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);
        } catch (RemoteException e) {
            assertTrue(false);
        }

        expectedState = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                regState, ServiceState.rilRadioTechnologyToNetworkType(radioTech), reasonForDenial,
                false, availableDataServices, null, "", maxDataCalls, false, false, false, vops);

        try {
            verify(mCallback, timeout(1000).times(1)).onRequestNetworkRegistrationInfoComplete(
                    eq(NetworkServiceCallback.RESULT_SUCCESS), eq(expectedState));
        } catch (RemoteException e) {
            assertTrue(false);
        }
    }
}
