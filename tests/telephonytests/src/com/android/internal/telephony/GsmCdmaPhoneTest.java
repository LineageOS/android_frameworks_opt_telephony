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

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.Phone.EVENT_ICC_CHANGED;
import static com.android.internal.telephony.Phone.EVENT_SRVCC_STATE_CHANGED;
import static com.android.internal.telephony.Phone.EVENT_UICC_APPS_ENABLEMENT_STATUS_CHANGED;
import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.WorkSource;
import android.preference.PreferenceManager;
import android.telecom.VideoProfile;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.LinkCapacityEstimate;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.test.SimulatedCommandsVerifier;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class GsmCdmaPhoneTest extends TelephonyTest {
    private static final String TEST_EMERGENCY_NUMBER = "555";
    @Mock
    private Handler mTestHandler;
    @Mock
    private UiccSlot mUiccSlot;
    @Mock
    private CommandsInterface mMockCi;

    //mPhoneUnderTest
    private GsmCdmaPhone mPhoneUT;

    private static final int EVENT_EMERGENCY_CALLBACK_MODE_EXIT = 1;
    private static final int EVENT_EMERGENCY_CALL_TOGGLE = 2;
    private static final int EVENT_SET_ICC_LOCK_ENABLED = 3;

    private void switchToGsm() {
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_GSM);
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_GSM}, null)));
        processAllMessages();
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());
    }

    private void switchToCdma() {
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_IS95A);
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_IS95A}, null)));
        processAllMessages();
        assertEquals(PhoneConstants.PHONE_TYPE_CDMA, mPhoneUT.getPhoneType());
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        doReturn(false).when(mSST).isDeviceShuttingDown();
        doReturn(true).when(mImsManager).isVolteEnabledByPlatform();

        mPhoneUT = new GsmCdmaPhone(mContext, mSimulatedCommands, mNotifier, true, 0,
            PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory, (c, p) -> mImsManager);
        mPhoneUT.setVoiceCallSessionStats(mVoiceCallSessionStats);
        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mUiccController).registerForIccChanged(eq(mPhoneUT), integerArgumentCaptor.capture(),
                nullable(Object.class));
        Message msg = Message.obtain();
        msg.what = integerArgumentCaptor.getValue();
        mPhoneUT.sendMessage(msg);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mPhoneUT.removeCallbacksAndMessages(null);
        mPhoneUT = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testPhoneTypeSwitch() {
        assertTrue(mPhoneUT.isPhoneTypeGsm());
        switchToCdma();
        assertTrue(mPhoneUT.isPhoneTypeCdmaLte());
    }

    @Test
    @SmallTest
    public void testGetServiceState() {
        ServiceState serviceState = new ServiceState();
        mSST.mSS = serviceState;
        assertEquals(serviceState, mPhoneUT.getServiceState());
    }

    @Test
    @SmallTest
    public void testGetMergedServiceState() throws Exception {
        ServiceState imsServiceState = new ServiceState();

        NetworkRegistrationInfo imsPsWwanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();

        NetworkRegistrationInfo imsPsWlanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();

        // Only PS states are tracked for IMS.
        imsServiceState.addNetworkRegistrationInfo(imsPsWwanRegInfo);
        imsServiceState.addNetworkRegistrationInfo(imsPsWlanRegInfo);

        // Voice reg state in this case is whether or not IMS is registered.
        imsServiceState.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        imsServiceState.setDataRegState(ServiceState.STATE_IN_SERVICE);
        imsServiceState.setIwlanPreferred(true);
        doReturn(imsServiceState).when(mImsPhone).getServiceState();

        replaceInstance(Phone.class, "mImsPhone", mPhoneUT, mImsPhone);

        ServiceState serviceState = new ServiceState();

        NetworkRegistrationInfo csWwanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .build();

        NetworkRegistrationInfo psWwanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .build();

        NetworkRegistrationInfo psWlanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();

        serviceState.addNetworkRegistrationInfo(csWwanRegInfo);
        serviceState.addNetworkRegistrationInfo(psWwanRegInfo);
        serviceState.addNetworkRegistrationInfo(psWlanRegInfo);
        serviceState.setVoiceRegState(ServiceState.STATE_OUT_OF_SERVICE);
        serviceState.setDataRegState(ServiceState.STATE_IN_SERVICE);
        serviceState.setIwlanPreferred(true);

        mSST.mSS = serviceState;
        mPhoneUT.mSST = mSST;

        ServiceState mergedServiceState = mPhoneUT.getServiceState();

        assertEquals(ServiceState.STATE_IN_SERVICE, mergedServiceState.getState());
        assertEquals(ServiceState.STATE_IN_SERVICE, mergedServiceState.getDataRegistrationState());
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, mergedServiceState.getDataNetworkType());
    }

    /**
     * Some vendors do not provide a voice registration for LTE when attached to LTE only (no CSFB
     * available). In this case, we should still get IN_SERVICE for voice service state, since
     * IMS is registered.
     */
    @Test
    @SmallTest
    public void testGetMergedServiceStateNoCsfb() throws Exception {
        ServiceState imsServiceState = new ServiceState();

        NetworkRegistrationInfo imsPsWwanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();

        NetworkRegistrationInfo imsPsWlanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .build();

        // Only PS states are tracked for IMS.
        imsServiceState.addNetworkRegistrationInfo(imsPsWwanRegInfo);
        imsServiceState.addNetworkRegistrationInfo(imsPsWlanRegInfo);

        // Voice reg state in this case is whether or not IMS is registered.
        imsServiceState.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        imsServiceState.setDataRegState(ServiceState.STATE_IN_SERVICE);
        imsServiceState.setIwlanPreferred(true);
        doReturn(imsServiceState).when(mImsPhone).getServiceState();

        replaceInstance(Phone.class, "mImsPhone", mPhoneUT, mImsPhone);

        ServiceState serviceState = new ServiceState();

        NetworkRegistrationInfo csWwanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UNKNOWN)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .build();

        NetworkRegistrationInfo psWwanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();

        NetworkRegistrationInfo psWlanRegInfo = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .build();

        serviceState.addNetworkRegistrationInfo(csWwanRegInfo);
        serviceState.addNetworkRegistrationInfo(psWwanRegInfo);
        serviceState.addNetworkRegistrationInfo(psWlanRegInfo);
        // No CSFB, voice is OOS for LTE only attach
        serviceState.setVoiceRegState(ServiceState.STATE_OUT_OF_SERVICE);
        serviceState.setDataRegState(ServiceState.STATE_IN_SERVICE);
        serviceState.setIwlanPreferred(true);

        mSST.mSS = serviceState;
        mPhoneUT.mSST = mSST;

        ServiceState mergedServiceState = mPhoneUT.getServiceState();

        assertEquals(ServiceState.STATE_IN_SERVICE, mergedServiceState.getState());
        assertEquals(ServiceState.STATE_IN_SERVICE, mergedServiceState.getDataRegistrationState());
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mergedServiceState.getDataNetworkType());
    }

    @Test
    @SmallTest
    public void testGetSubscriberIdForGsmPhone() {
        final String subscriberId = "123456789";
        IccRecords iccRecords = Mockito.mock(IccRecords.class);
        doReturn(subscriberId).when(iccRecords).getIMSI();
        doReturn(iccRecords).when(mUiccController)
                .getIccRecords(anyInt() /* phoneId */, eq(UiccController.APP_FAM_3GPP));

        // Ensure the phone type is GSM
        GsmCdmaPhone spyPhone = spy(mPhoneUT);
        doReturn(false).when(spyPhone).isPhoneTypeCdma();
        doReturn(false).when(spyPhone).isPhoneTypeCdmaLte();
        doReturn(true).when(spyPhone).isPhoneTypeGsm();

        assertEquals(subscriberId, spyPhone.getSubscriberId());
    }

    @Test
    @SmallTest
    public void testGetSubscriberIdForCdmaLtePhone() {
        final String subscriberId = "abcdefghijk";
        IccRecords iccRecords = Mockito.mock(IccRecords.class);
        doReturn(subscriberId).when(iccRecords).getIMSI();
        doReturn(iccRecords).when(mUiccController)
                .getIccRecords(anyInt() /* phoneId */, eq(UiccController.APP_FAM_3GPP));

        // Ensure the phone type is CdmaLte
        GsmCdmaPhone spyPhone = spy(mPhoneUT);
        doReturn(false).when(spyPhone).isPhoneTypeCdma();
        doReturn(true).when(spyPhone).isPhoneTypeCdmaLte();
        doReturn(false).when(spyPhone).isPhoneTypeGsm();

        assertEquals(subscriberId, spyPhone.getSubscriberId());
    }

    @Test
    @SmallTest
    public void testGetSubscriberIdForCdmaPhone() {
        final String subscriberId = "987654321";
        doReturn(subscriberId).when(mSST).getImsi();

        // Ensure the phone type is GSM
        GsmCdmaPhone spyPhone = spy(mPhoneUT);
        doReturn(true).when(spyPhone).isPhoneTypeCdma();
        doReturn(false).when(spyPhone).isPhoneTypeCdmaLte();
        doReturn(false).when(spyPhone).isPhoneTypeGsm();

        assertEquals(subscriberId, spyPhone.getSubscriberId());
    }

    @Test
    @SmallTest
    public void testGetCellLocation() {
        // GSM
        CellIdentity cellLocation = new CellIdentityGsm();
        WorkSource workSource = new WorkSource(Process.myUid(),
            mContext.getPackageName());
        doReturn(cellLocation).when(mSST).getCellIdentity();
        assertEquals(cellLocation, mPhoneUT.getCurrentCellIdentity());

        // Switch to CDMA
        switchToCdma();

        CellIdentityCdma cdmaCellLocation = new CellIdentityCdma();
        doReturn(cdmaCellLocation).when(mSST).getCellIdentity();

        CellIdentityCdma actualCellLocation =
                (CellIdentityCdma) mPhoneUT.getCurrentCellIdentity();

        assertEquals(actualCellLocation, cdmaCellLocation);
    }

    @Test
    @SmallTest
    public void testGetPhoneType() {
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());

        // Switch to CDMA
        switchToCdma();

        assertEquals(PhoneConstants.PHONE_TYPE_CDMA, mPhoneUT.getPhoneType());
    }

    @Test
    @SmallTest
    public void testGetDataConnectionState() {
        // There are several cases possible. Testing few of them for now.
        // 1. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn != APN_TYPE_EMERGENCY
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST).getCurrentDataConnectionState();
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhoneUT.getDataConnectionState(
                ApnSetting.TYPE_ALL_STRING));

        // 2. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY, apn
        // not connected
        doReturn(DctConstants.State.IDLE).when(mDcTracker).getState(
                ApnSetting.TYPE_EMERGENCY_STRING);
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhoneUT.getDataConnectionState(
                ApnSetting.TYPE_EMERGENCY_STRING));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN is connected, callTracker state = idle
        doReturn(DctConstants.State.CONNECTED).when(mDcTracker).getState(
                ApnSetting.TYPE_EMERGENCY_STRING);
        mCT.mState = PhoneConstants.State.IDLE;
        assertEquals(PhoneConstants.DataState.CONNECTED, mPhoneUT.getDataConnectionState(
                ApnSetting.TYPE_EMERGENCY_STRING));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN enabled and CONNECTED, callTracker state != idle, !isConcurrentVoiceAndDataAllowed
        mCT.mState = PhoneConstants.State.RINGING;
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        assertEquals(PhoneConstants.DataState.SUSPENDED, mPhoneUT.getDataConnectionState(
                ApnSetting.TYPE_EMERGENCY_STRING));
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommands() {
        try {
            // Switch to CDMA
            switchToCdma();

            assertFalse(mPhoneUT.handleInCallMmiCommands("0"));

            // Switch to GSM
            switchToGsm();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            // !isInCall
            assertFalse(mPhoneUT.handleInCallMmiCommands("0"));

            // isInCall
            doReturn(GsmCdmaCall.State.ACTIVE).when(mGsmCdmaCall).getState();
            assertTrue(mPhoneUT.handleInCallMmiCommands("0"));

            // empty dialString
            assertFalse(mPhoneUT.handleInCallMmiCommands(""));
            assertFalse(mPhoneUT.handleInCallMmiCommands(null));

        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    @SmallTest
    public void testDial() throws Exception {
        try {
            mSST.mSS = mServiceState;
            doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            replaceInstance(Phone.class, "mImsPhone", mPhoneUT, mImsPhone);

            Connection connection = mPhoneUT.dial("1234567890",
                    new PhoneInternalInterface.DialArgs.Builder().build());
            verify(mCT).dialGsm(eq("1234567890"), any(PhoneInternalInterface.DialArgs.class));
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testWpsDialOverCs() throws Exception {
        try {
            setupForWpsCallTest();

            mContextFixture.getCarrierConfigBundle().putBoolean(
                    CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, false);

            mPhoneUT.dial("*27216505551212", new PhoneInternalInterface.DialArgs.Builder().build());

            verify(mCT).dialGsm(eq("*27216505551212"), any(PhoneInternalInterface.DialArgs.class));
            verify(mImsCT).hangupAllConnections();
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testClirCs() {
        mPhoneUT.mCi = mMockCi;
        // Start out with no preference set and ensure CommandsInterface receives setClir with
        // the default set.
        mPhoneUT.sendEmptyMessage(Phone.EVENT_REGISTERED_TO_NETWORK);
        processAllMessages();
        verify(mMockCi).setCLIR(eq(CommandsInterface.CLIR_DEFAULT), any());
        // Now set the CLIR mode explicitly
        mPhoneUT.setOutgoingCallerIdDisplay(CommandsInterface.CLIR_SUPPRESSION, null);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockCi).setCLIR(eq(CommandsInterface.CLIR_SUPPRESSION), messageCaptor.capture());
        Message message = messageCaptor.getValue();
        assertNotNull(message);
        message.obj = AsyncResult.forMessage(message);
        // Now Call registered to network again and the CLIR mode sent should reflect the new value.
        mPhoneUT.sendEmptyMessage(Phone.EVENT_REGISTERED_TO_NETWORK);
        processAllMessages();
        verify(mMockCi).setCLIR(eq(CommandsInterface.CLIR_SUPPRESSION), any());
    }

    @Test
    @SmallTest
    public void testWpsClirActiveDialOverCs() throws Exception {
        try {
            setupForWpsCallTest();

            mContextFixture.getCarrierConfigBundle().putBoolean(
                    CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, false);

            mPhoneUT.dial("*31#*27216505551212",
                    new PhoneInternalInterface.DialArgs.Builder().build());

            verify(mCT).dialGsm("*27216505551212", CommandsInterface.CLIR_SUPPRESSION, null, null);
            verify(mImsCT).hangupAllConnections();
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testWpsClirInactiveDialOverCs() throws Exception {
        try {
            setupForWpsCallTest();

            mContextFixture.getCarrierConfigBundle().putBoolean(
                    CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, false);

            mPhoneUT.dial("#31#*27216505551212",
                    new PhoneInternalInterface.DialArgs.Builder().build());

            verify(mCT).dialGsm("*27216505551212", CommandsInterface.CLIR_INVOCATION, null, null);
            verify(mImsCT).hangupAllConnections();
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testWpsDialOverIms() throws Exception {
        try {
            setupForWpsCallTest();

            mContextFixture.getCarrierConfigBundle().putBoolean(
                    CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, true);

            mPhoneUT.dial("*27216505551212",
                    new PhoneInternalInterface.DialArgs.Builder().build());
            verify(mCT).dialGsm(eq("*27216505551212"), any(PhoneInternalInterface.DialArgs.class));
            verify(mImsCT, never()).hangupAllConnections();
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testWpsClirActiveDialOverIms() throws Exception {
        try {
            setupForWpsCallTest();

            mContextFixture.getCarrierConfigBundle().putBoolean(
                    CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, true);

            mPhoneUT.dial("*31#*27216505551212",
                    new PhoneInternalInterface.DialArgs.Builder().build());
            verify(mCT).dialGsm("*27216505551212", CommandsInterface.CLIR_SUPPRESSION, null, null);
            verify(mImsCT, never()).hangupAllConnections();
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testWpsClirInactiveDialOverIms() throws Exception {
        try {
            setupForWpsCallTest();

            mContextFixture.getCarrierConfigBundle().putBoolean(
                    CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, true);

            mPhoneUT.dial("#31#*27216505551212",
                    new PhoneInternalInterface.DialArgs.Builder().build());

            verify(mCT).dialGsm("*27216505551212", CommandsInterface.CLIR_INVOCATION, null, null);
            verify(mImsCT, never()).hangupAllConnections();
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testHandlePinMmi() {
        assertFalse(mPhoneUT.handlePinMmi("1234567890"));
    }

    @Test
    @SmallTest
    public void testEmergencySmsMode() {
        String emergencyNumber = "111";
        String nonEmergencyNumber = "222";
        int timeout = 200;
        mContextFixture.getCarrierConfigBundle().putInt(
                CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT, timeout);
        doReturn(true).when(mTelephonyManager).isEmergencyNumber(emergencyNumber);

        mPhoneUT.notifySmsSent(nonEmergencyNumber);
        processAllMessages();
        assertFalse(mPhoneUT.isInEmergencySmsMode());

        mPhoneUT.notifySmsSent(emergencyNumber);
        processAllMessages();
        assertTrue(mPhoneUT.isInEmergencySmsMode());
        // mTimeLastEmergencySmsSentMs uses System.currentTimeMillis()
        waitForMs(timeout + 5);
        processAllMessages();
        assertFalse(mPhoneUT.isInEmergencySmsMode());

        // Feature not supported
        mContextFixture.getCarrierConfigBundle().putInt(
                CarrierConfigManager.KEY_EMERGENCY_SMS_MODE_TIMER_MS_INT, 0);
        mPhoneUT.notifySmsSent(emergencyNumber);
        processAllMessages();
        assertFalse(mPhoneUT.isInEmergencySmsMode());
    }

    @Test
    @SmallTest
    public void testSendBurstDtmf() {
        //Should do nothing for GSM
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        switchToCdma();
        //invalid character
        mPhoneUT.sendBurstDtmf("12345a67890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        //state IDLE
        mCT.mState = PhoneConstants.State.IDLE;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        //state RINGING
        mCT.mState = PhoneConstants.State.RINGING;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        mCT.mState = PhoneConstants.State.OFFHOOK;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier).sendBurstDtmf("1234567890", 0, 0, null);
    }

    @Test
    @SmallTest
    public void testVoiceMailNumberGsm() {
        String voiceMailNumber = "1234567890";
        // first test for GSM
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());

        // no resource or sharedPreference set -- should be null
        assertEquals(null, mPhoneUT.getVoiceMailNumber());

        // config_telephony_use_own_number_for_voicemail
        mContextFixture.getCarrierConfigBundle()
                .putBoolean(CarrierConfigManager
                        .KEY_CONFIG_TELEPHONY_USE_OWN_NUMBER_FOR_VOICEMAIL_BOOL, true);
        doReturn(voiceMailNumber).when(mSimRecords).getMsisdnNumber();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from config
        mContextFixture.getCarrierConfigBundle().
                putString(CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_STRING, voiceMailNumber);
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from config for roaming network
        String voiceMailNumberForRoaming = "1234567892";
        mContextFixture.getCarrierConfigBundle()
                .putString(CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_ROAMING_STRING,
                        voiceMailNumberForRoaming);

        // voicemail number from config for roaming network and ims unregistered
        String voiceMailNumberForImsRoamingAndUnregistered = "1234567893";
        mContextFixture.getCarrierConfigBundle().putString(
                CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_ROAMING_AND_IMS_UNREGISTERED_STRING,
                        voiceMailNumberForImsRoamingAndUnregistered);

        //Verify voicemail number for home
        doReturn(false).when(mSST.mSS).getRoaming();
        doReturn(true).when(mSST).isImsRegistered();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
        //Move to ims condition, verify voicemail number for ims unregistered
        doReturn(false).when(mSST).isImsRegistered();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
        //Move to roaming condition, verify voicemail number for roaming
        doReturn(true).when(mSST.mSS).getRoaming();
        assertEquals(voiceMailNumberForImsRoamingAndUnregistered, mPhoneUT.getVoiceMailNumber());
        //Move to ims condition, verify voicemail number for roaming
        doReturn(true).when(mSST).isImsRegistered();
        assertEquals(voiceMailNumberForRoaming, mPhoneUT.getVoiceMailNumber());
        //Move to home condition, verify voicemail number for home
        doReturn(false).when(mSST.mSS).getRoaming();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number that is explicitly set
        voiceMailNumber = "1234567891";
        mPhoneUT.setVoiceMailNumber("alphaTag", voiceMailNumber, null);
        verify(mSimRecords).setVoiceMailNumber(eq("alphaTag"), eq(voiceMailNumber),
                nullable(Message.class));

        doReturn(voiceMailNumber).when(mSimRecords).getVoiceMailNumber();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
    }

    @Test
    @SmallTest
    public void testVoiceMailNumberCdma() {
        switchToCdma();
        String voiceMailNumber = "1234567890";

        // config_telephony_use_own_number_for_voicemail
        mContextFixture.getCarrierConfigBundle()
                .putBoolean(CarrierConfigManager
                                .KEY_CONFIG_TELEPHONY_USE_OWN_NUMBER_FOR_VOICEMAIL_BOOL, true);
        doReturn(voiceMailNumber).when(mSST).getMdnNumber();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from config
        voiceMailNumber = "1234567891";
        mContextFixture.getCarrierConfigBundle().
                putString(CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_STRING, voiceMailNumber);
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from config for roaming network
        String voiceMailNumberForRoaming = "1234567892";
        mContextFixture.getCarrierConfigBundle()
                .putString(CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_ROAMING_STRING,
                        voiceMailNumberForRoaming);

        // voicemail number from config for roaming network and ims unregistered
        String voiceMailNumberForImsRoamingAndUnregistered = "1234567893";
        mContextFixture.getCarrierConfigBundle().putString(
                CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_ROAMING_AND_IMS_UNREGISTERED_STRING,
                        voiceMailNumberForImsRoamingAndUnregistered);

        //Verify voicemail number for home
        doReturn(false).when(mSST.mSS).getRoaming();
        doReturn(true).when(mSST).isImsRegistered();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
        //Move to ims condition, verify voicemail number for ims unregistered
        doReturn(false).when(mSST).isImsRegistered();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
        //Move to roaming condition, verify voicemail number for roaming
        doReturn(true).when(mSST.mSS).getRoaming();
        assertEquals(voiceMailNumberForImsRoamingAndUnregistered, mPhoneUT.getVoiceMailNumber());
        //Move to ims condition, verify voicemail number for roaming
        doReturn(true).when(mSST).isImsRegistered();
        assertEquals(voiceMailNumberForRoaming, mPhoneUT.getVoiceMailNumber());
        //Move to home condition, verify voicemail number for home
        doReturn(false).when(mSST.mSS).getRoaming();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from sharedPreference
        voiceMailNumber = "1234567893";
        mPhoneUT.setVoiceMailNumber("alphaTag", voiceMailNumber, null);
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        InOrder inOrder = inOrder(mSimRecords);
        inOrder.verify(mSimRecords).setVoiceMailNumber(eq("alphaTag"), eq(voiceMailNumber),
                messageArgumentCaptor.capture());

        // SIM does not support voicemail number (IccVmNotSupportedException) so should be saved in
        // shared pref
        Message msg = messageArgumentCaptor.getValue();
        AsyncResult.forMessage(msg).exception =
                new IccVmNotSupportedException("setVoiceMailNumber not implemented");
        msg.sendToTarget();
        processAllMessages();

        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from SIM
        voiceMailNumber = "1234567894";
        mPhoneUT.setVoiceMailNumber("alphaTag", voiceMailNumber, null);
        messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        inOrder.verify(mSimRecords).setVoiceMailNumber(eq("alphaTag"), eq(voiceMailNumber),
                messageArgumentCaptor.capture());

        // successfully saved on SIM
        msg = messageArgumentCaptor.getValue();
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        doReturn(voiceMailNumber).when(mSimRecords).getVoiceMailNumber();

        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
    }

    @FlakyTest
    @Test
    @Ignore
    public void testVoiceMailCount() {
        // initial value
        assertEquals(0, mPhoneUT.getVoiceMessageCount());

        // old sharedPreference set (testing upgrade from M to N scenario)
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String imsi = "1234567890";
        editor.putString("vm_id_key", imsi);
        editor.putInt("vm_count_key", 5);
        editor.apply();
        doReturn(imsi).when(mSimRecords).getIMSI();

        // updateVoiceMail should read old shared pref and delete it and new sharedPref should be
        // updated now
        doReturn(-1).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.updateVoiceMail();
        assertEquals(5, mPhoneUT.getVoiceMessageCount());
        assertEquals(null, sharedPreferences.getString("vm_id_key", null));
        assertEquals(5, sharedPreferences.getInt("vm_count_key" + mPhoneUT.getSubId(), 0));

        // sim records return count as 0, that overrides shared preference
        doReturn(0).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.updateVoiceMail();
        assertEquals(0, mPhoneUT.getVoiceMessageCount());

        // sim records return count as -1
        doReturn(-1).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.updateVoiceMail();
        assertEquals(-1, mPhoneUT.getVoiceMessageCount());

        // sim records return count as -1 and sharedPreference says 0
        mPhoneUT.setVoiceMessageCount(0);
        mPhoneUT.updateVoiceMail();
        assertEquals(-1, mPhoneUT.getVoiceMessageCount());

        // sim records return count as -1 and sharedPreference says 2
        mPhoneUT.setVoiceMessageCount(2);
        mPhoneUT.updateVoiceMail();
        assertEquals(2, mPhoneUT.getVoiceMessageCount());

        // sim records return count as 0 and sharedPreference says 2
        doReturn(0).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.setVoiceMessageCount(2);
        mPhoneUT.updateVoiceMail();
        assertEquals(0, mPhoneUT.getVoiceMessageCount());
    }

    @Test
    @SmallTest
    public void testGetCallForwardingOption() {
        // invalid reason (-1)
        mPhoneUT.getCallForwardingOption(-1, null);
        verify(mSimulatedCommandsVerifier, times(0)).queryCallForwardStatus(
                anyInt(), anyInt(), nullable(String.class), nullable(Message.class));

        // valid reason
        String imsi = "1234567890";
        doReturn(imsi).when(mSimRecords).getIMSI();
        mPhoneUT.getCallForwardingOption(CF_REASON_UNCONDITIONAL, null);
        verify(mSimulatedCommandsVerifier).queryCallForwardStatus(
                eq(CF_REASON_UNCONDITIONAL), eq(CommandsInterface.SERVICE_CLASS_VOICE),
                nullable(String.class), nullable(Message.class));
        processAllMessages();
        verify(mSimRecords).setVoiceCallForwardingFlag(anyInt(), anyBoolean(),
                nullable(String.class));

        // should have updated shared preferences
        SharedPreferences sharedPreferences = PreferenceManager.
                getDefaultSharedPreferences(mContext);
        assertEquals(IccRecords.CALL_FORWARDING_STATUS_DISABLED,
                sharedPreferences.getInt(Phone.CF_STATUS + mPhoneUT.getSubId(),
                        IccRecords.CALL_FORWARDING_STATUS_ENABLED));

        // clean up
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(Phone.CF_STATUS + mPhoneUT.getSubId());
        editor.apply();
    }

    @Test
    @SmallTest
    public void testSetCallForwardingOption() {
        String cfNumber = "1234567890";

        // invalid action
        mPhoneUT.setCallForwardingOption(-1, CF_REASON_UNCONDITIONAL,
                cfNumber, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).setCallForward(anyInt(), anyInt(), anyInt(),
                nullable(String.class), anyInt(), nullable(Message.class));

        // valid action
        mPhoneUT.setCallForwardingOption(CF_ACTION_ENABLE, CF_REASON_UNCONDITIONAL, cfNumber, 0,
                null);
        verify(mSimulatedCommandsVerifier).setCallForward(eq(CF_ACTION_ENABLE),
                eq(CF_REASON_UNCONDITIONAL), anyInt(), eq(cfNumber), eq(0),
                nullable(Message.class));
        processAllMessages();
        verify(mSimRecords).setVoiceCallForwardingFlag(anyInt(), anyBoolean(), eq(cfNumber));
    }

    /**
     * GsmCdmaPhone handles a lot of messages. This function verifies behavior for messages that are
     * received when obj is created and that are received on phone type switch
     */
    @FlakyTest
    @Ignore
    @Test
    @SmallTest
    public void testHandleInitialMessages() {
        // EVENT_RADIO_AVAILABLE
        verify(mSimulatedCommandsVerifier).getBasebandVersion(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).getDeviceIdentity(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).getRadioCapability(nullable(Message.class));
        // once as part of constructor, and once on radio available
        verify(mSimulatedCommandsVerifier, times(2)).startLceService(anyInt(), anyBoolean(),
                nullable(Message.class));

        // EVENT_RADIO_ON
        verify(mSimulatedCommandsVerifier).getVoiceRadioTechnology(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).setPreferredNetworkType(
                eq(RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA), nullable(Message.class));

        // verify responses for above requests:
        // baseband version
        verify(mTelephonyManager).setBasebandVersionForPhone(eq(mPhoneUT.getPhoneId()),
                nullable(String.class));
        // IMEI
        assertEquals(SimulatedCommands.FAKE_IMEI, mPhoneUT.getImei());
        // IMEISV
        assertEquals(SimulatedCommands.FAKE_IMEISV, mPhoneUT.getDeviceSvn());
        // radio capability
        verify(mSimulatedCommandsVerifier).getNetworkSelectionMode(nullable(Message.class));

        switchToCdma(); // this leads to eventRadioAvailable handling on cdma

        // EVENT_RADIO_AVAILABLE
        verify(mSimulatedCommandsVerifier, times(2)).getBasebandVersion(nullable(Message.class));
        verify(mSimulatedCommandsVerifier, times(2)).getDeviceIdentity(nullable(Message.class));
        verify(mSimulatedCommandsVerifier, times(3)).startLceService(anyInt(), anyBoolean(),
                nullable(Message.class));

        // EVENT_RADIO_ON
        verify(mSimulatedCommandsVerifier, times(2)).getVoiceRadioTechnology(
                nullable(Message.class));
        // once on radio on, and once on get baseband version
        verify(mSimulatedCommandsVerifier, times(3)).setPreferredNetworkType(
                eq(RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA), nullable(Message.class));

        // verify responses for above requests:
        // baseband version
        verify(mTelephonyManager, times(2)).setBasebandVersionForPhone(eq(mPhoneUT.getPhoneId()),
                nullable(String.class));
        // device identity
        assertEquals(SimulatedCommands.FAKE_IMEI, mPhoneUT.getImei());
        assertEquals(SimulatedCommands.FAKE_IMEISV, mPhoneUT.getDeviceSvn());
        assertEquals(SimulatedCommands.FAKE_ESN, mPhoneUT.getEsn());
        assertEquals(SimulatedCommands.FAKE_MEID, mPhoneUT.getMeid());
    }

    @Test
    public void testZeroMeid() {
        doReturn(false).when(mSST).isDeviceShuttingDown();

        SimulatedCommands sc = new SimulatedCommands() {
            @Override
            public void getDeviceIdentity(Message response) {
                SimulatedCommandsVerifier.getInstance().getDeviceIdentity(response);
                resultSuccess(response, new String[] {FAKE_IMEI, FAKE_IMEISV, FAKE_ESN, "0000000"});
            }
        };

        Phone phone = new GsmCdmaPhone(mContext, sc, mNotifier, true, 0,
                PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory, (c, p) -> mImsManager);
        phone.setVoiceCallSessionStats(mVoiceCallSessionStats);
        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mUiccController).registerForIccChanged(eq(phone), integerArgumentCaptor.capture(),
                nullable(Object.class));
        Message msg = Message.obtain();
        msg.what = integerArgumentCaptor.getValue();
        phone.sendMessage(msg);
        processAllMessages();

        assertNull(phone.getMeid());
    }

    @Test
    @SmallTest
    public void testEmergencyCallbackMessages() throws Exception {
        verify(mSimulatedCommandsVerifier).setEmergencyCallbackMode(eq(mPhoneUT), anyInt(),
                nullable(Object.class));
        verify(mSimulatedCommandsVerifier).registerForExitEmergencyCallbackMode(eq(mPhoneUT),
                anyInt(), nullable(Object.class));

        // verify handling of emergency callback mode
        mSimulatedCommands.notifyEmergencyCallbackMode();
        processAllMessages();

        verifyEcbmIntentSent(1 /*times*/, true /*isInEcm*/);
        assertTrue(mPhoneUT.isInEcm());

        // verify that wakeLock is acquired in ECM
        assertTrue(mPhoneUT.getWakeLock().isHeld());

        mPhoneUT.setOnEcbModeExitResponse(mTestHandler, EVENT_EMERGENCY_CALLBACK_MODE_EXIT, null);
        mPhoneUT.registerForEmergencyCallToggle(mTestHandler, EVENT_EMERGENCY_CALL_TOGGLE, null);

        // verify handling of emergency callback mode exit
        mSimulatedCommands.notifyExitEmergencyCallbackMode();
        processAllMessages();

        verifyEcbmIntentSent(2 /*times*/, false /*isInEcm*/);
        assertFalse(mPhoneUT.isInEcm());

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        // verify EcmExitRespRegistrant and mEmergencyCallToggledRegistrants are notified
        verify(mTestHandler, times(2)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        List<Message> msgList = messageArgumentCaptor.getAllValues();
        assertEquals(EVENT_EMERGENCY_CALLBACK_MODE_EXIT, msgList.get(0).what);
        assertEquals(EVENT_EMERGENCY_CALL_TOGGLE, msgList.get(1).what);

        // verify setInternalDataEnabled
        verify(mDataEnabledSettings).setInternalDataEnabled(true);

        // verify wakeLock released
        assertFalse(mPhoneUT.getWakeLock().isHeld());
    }

    private void verifyEcbmIntentSent(int times, boolean isInEcm) throws Exception {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(times)).sendStickyBroadcastAsUser(intentArgumentCaptor.capture(),
                any());
        Intent intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(isInEcm, intent.getBooleanExtra(
                TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false));
    }

    @Test
    @SmallTest
    public void testEcmCancelledPreservedThroughSrvcc() throws Exception {
        replaceInstance(Phone.class, "mImsPhone", mPhoneUT, mImsPhone);
        assertFalse(mPhoneUT.isEcmCanceledForEmergency());
        // Set ECM cancelled state on ImsPhone to be transferred via migrateFrom
        doReturn(true).when(mImsPhone).isEcmCanceledForEmergency();
        verify(mSimulatedCommandsVerifier).registerForSrvccStateChanged(any(),
                eq(EVENT_SRVCC_STATE_CHANGED), any());

        // Start SRVCC
        Message msg = Message.obtain();
        msg.what = EVENT_SRVCC_STATE_CHANGED;
        msg.obj = new AsyncResult(null, new int[]{TelephonyManager.SRVCC_STATE_HANDOVER_STARTED},
                null);
        mPhoneUT.sendMessage(msg);
        processAllMessages();

        // verify ECM cancelled is transferred correctly.
        assertTrue(mPhoneUT.isEcmCanceledForEmergency());
    }

    @Test
    @SmallTest
    public void testModemResetInEmergencyCallbackMessages() {
        verify(mSimulatedCommandsVerifier).setEmergencyCallbackMode(eq(mPhoneUT), anyInt(),
                nullable(Object.class));
        verify(mSimulatedCommandsVerifier).registerForModemReset(eq(mPhoneUT),
                anyInt(), nullable(Object.class));

        switchToCdma();
        // verify handling of emergency callback mode
        mSimulatedCommands.notifyEmergencyCallbackMode();
        processAllMessages();

        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        try {
            verify(mContext, atLeast(1)).sendStickyBroadcastAsUser(intentArgumentCaptor.capture(),
                    any());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        Intent intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(true, intent.getBooleanExtra(
                TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false));
        assertEquals(true, mPhoneUT.isInEcm());

        // verify that wakeLock is acquired in ECM
        assertEquals(true, mPhoneUT.getWakeLock().isHeld());

        mPhoneUT.setOnEcbModeExitResponse(mTestHandler, EVENT_EMERGENCY_CALLBACK_MODE_EXIT, null);
        mPhoneUT.registerForEmergencyCallToggle(mTestHandler, EVENT_EMERGENCY_CALL_TOGGLE, null);

        // verify handling of emergency callback mode exit when modem resets
        mSimulatedCommands.notifyModemReset();
        processAllMessages();

        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        try {
            verify(mContext, atLeast(2)).sendStickyBroadcastAsUser(intentArgumentCaptor.capture(),
                    any());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(false, intent.getBooleanExtra(
                TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, true));
        assertEquals(false, mPhoneUT.isInEcm());

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        // verify EcmExitRespRegistrant and mEmergencyCallToggledRegistrants are notified
        verify(mTestHandler, times(2)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        List<Message> msgList = messageArgumentCaptor.getAllValues();
        assertEquals(EVENT_EMERGENCY_CALLBACK_MODE_EXIT, msgList.get(0).what);
        assertEquals(EVENT_EMERGENCY_CALL_TOGGLE, msgList.get(1).what);

        // verify setInternalDataEnabled
        verify(mDataEnabledSettings).setInternalDataEnabled(true);

        // verify wakeLock released
        assertEquals(false, mPhoneUT.getWakeLock().isHeld());
    }

    @Test
    @SmallTest
    public void testCallForwardingIndicator() {
        doReturn(IccRecords.CALL_FORWARDING_STATUS_UNKNOWN).when(mSimRecords).
                getVoiceCallForwardingFlag();

        // invalid subId
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionController).
                getSubIdUsingPhoneId(anyInt());
        assertEquals(false, mPhoneUT.getCallForwardingIndicator());

        // valid subId, sharedPreference not present
        int subId1 = 0;
        int subId2 = 1;
        doReturn(subId1).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());
        assertEquals(false, mPhoneUT.getCallForwardingIndicator());

        // old sharedPreference present
        String imsi = "1234";
        doReturn(imsi).when(mSimRecords).getIMSI();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(Phone.CF_ID, imsi);
        editor.putInt(Phone.CF_STATUS, IccRecords.CALL_FORWARDING_STATUS_ENABLED);
        editor.apply();
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // old sharedPreference should be removed now
        assertEquals(null, sp.getString(Phone.CF_ID, null));
        assertEquals(IccRecords.CALL_FORWARDING_STATUS_UNKNOWN,
                sp.getInt(Phone.CF_ID, IccRecords.CALL_FORWARDING_STATUS_UNKNOWN));

        // now verify value from new sharedPreference
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // check for another subId
        doReturn(subId2).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());
        assertEquals(false, mPhoneUT.getCallForwardingIndicator());

        // set value for the new subId in sharedPreference
        editor.putInt(Phone.CF_STATUS + subId2, IccRecords.CALL_FORWARDING_STATUS_ENABLED);
        editor.apply();
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // switching back to previous subId, stored value should still be available
        doReturn(subId1).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // cleanup
        editor.remove(Phone.CF_STATUS + subId1);
        editor.remove(Phone.CF_STATUS + subId2);
        editor.apply();
    }

    @Test
    @SmallTest
    public void testGetIccCardUnknownAndAbsent() {
        // If UiccSlot.isStateUnknown is true, we should return a placeholder IccCard with the state
        // set to UNKNOWN
        doReturn(null).when(mUiccController).getUiccProfileForPhone(anyInt());
        UiccSlot mockSlot = mock(UiccSlot.class);
        doReturn(mockSlot).when(mUiccController).getUiccSlotForPhone(anyInt());
        doReturn(true).when(mockSlot).isStateUnknown();

        IccCard iccCard = mPhoneUT.getIccCard();
        assertEquals(IccCardConstants.State.UNKNOWN, iccCard.getState());

        // if isStateUnknown is false, we should return a placeholder IccCard with the state set to
        // ABSENT
        doReturn(false).when(mockSlot).isStateUnknown();
        iccCard = mPhoneUT.getIccCard();
        assertEquals(IccCardConstants.State.ABSENT, iccCard.getState());
    }

    @Test
    @SmallTest
    public void testGetEmptyIccCard() {
        doReturn(null).when(mUiccController).getUiccProfileForPhone(anyInt());

        IccCard iccCard = mPhoneUT.getIccCard();

        // The iccCard should be a placeholder object, not null.
        assertTrue(!(iccCard instanceof UiccProfile));

        assertTrue(iccCard != null);
        assertEquals(IccCardConstants.State.UNKNOWN, iccCard.getState());
        assertEquals(null, iccCard.getIccRecords());
        assertEquals(false, iccCard.getIccLockEnabled());
        assertEquals(false, iccCard.getIccFdnEnabled());
        assertEquals(false, iccCard.isApplicationOnIcc(
                IccCardApplicationStatus.AppType.APPTYPE_SIM));
        assertEquals(false, iccCard.hasIccCard());
        assertEquals(false, iccCard.getIccPin2Blocked());
        assertEquals(false, iccCard.getIccPuk2Blocked());

        Message onComplete = mTestHandler.obtainMessage(EVENT_SET_ICC_LOCK_ENABLED);
        iccCard.setIccLockEnabled(true, "password", onComplete);

        processAllMessages();

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        // Verify that message is sent back with exception.
        verify(mTestHandler, times(1)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        Message message = messageArgumentCaptor.getAllValues().get(0);
        AsyncResult ret = (AsyncResult) message.obj;
        assertEquals(EVENT_SET_ICC_LOCK_ENABLED, message.what);
        assertTrue(ret.exception != null);
    }

    @Test
    @SmallTest
    public void testGetCsCallRadioTech() {
        ServiceState ss = new ServiceState();
        mSST.mSS = ss;

        // vrs in-service, vrat umts, expected umts
        ss.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .build();
        ss.addNetworkRegistrationInfo(nri);
        assertEquals(mPhoneUT.getCsCallRadioTech(), ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);

        // vrs oos, vrat umts, expected unknown
        ss.setVoiceRegState(ServiceState.STATE_OUT_OF_SERVICE);
        assertEquals(mPhoneUT.getCsCallRadioTech(), ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN);

        // vrs in-service, vrat lte, expected unknown
        ss.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        nri = new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();
        ss.addNetworkRegistrationInfo(nri);
        assertEquals(mPhoneUT.getCsCallRadioTech(), ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN);
    }

    @Test
    @SmallTest
    public void testGetLine1NumberForGsmPhone() {
        final String msisdn = "+1234567890";
        doReturn(msisdn).when(mSimRecords).getMsisdnNumber();

        switchToGsm();
        assertEquals(msisdn, mPhoneUT.getLine1Number());
    }

    @Test
    @SmallTest
    public void testGetLine1NumberForCdmaPhone() {
        final String mdn = "1234567890";
        final String msisdn = "+1234567890";
        doReturn(mdn).when(mSST).getMdnNumber();
        doReturn(msisdn).when(mSimRecords).getMsisdnNumber();

        switchToCdma();
        assertEquals(mdn, mPhoneUT.getLine1Number());

        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_USE_USIM_BOOL, true);
        assertEquals(msisdn, mPhoneUT.getLine1Number());
    }

    @Test
    @SmallTest
    public void testEnableUiccApplications() throws Exception {
        mPhoneUT.mCi = mMockCi;
        // UiccSlot is null. Doing nothing.
        mPhoneUT.enableUiccApplications(true, null);
        verify(mMockCi, never()).enableUiccApplications(anyBoolean(), any());

        // Card state is not PRESENT. Doing nothing.
        doReturn(mUiccSlot).when(mUiccController).getUiccSlotForPhone(anyInt());
        doReturn(IccCardStatus.CardState.CARDSTATE_ABSENT).when(mUiccSlot).getCardState();
        mPhoneUT.enableUiccApplications(true, null);
        verify(mMockCi, never()).enableUiccApplications(anyBoolean(), any());

        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mUiccSlot).getCardState();
        Message message = Message.obtain();
        mPhoneUT.enableUiccApplications(true, message);
        verify(mMockCi).enableUiccApplications(eq(true), eq(message));
    }

    @Test
    @SmallTest
    public void testReapplyUiccApplicationEnablementNotNeeded() throws Exception {
        mPhoneUT.mCi = mMockCi;
        // UiccSlot is null, or not present, or mUiccApplicationsEnabled is not available, or IccId
        // is not available, Doing nothing.
        doReturn(mUiccSlot).when(mUiccController).getUiccSlotForPhone(anyInt());
        Message.obtain(mPhoneUT, EVENT_ICC_CHANGED, null).sendToTarget();
        processAllMessages();
        doReturn(IccCardStatus.CardState.CARDSTATE_ABSENT).when(mUiccSlot).getCardState();
        Message.obtain(mPhoneUT, EVENT_ICC_CHANGED, null).sendToTarget();
        processAllMessages();
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mUiccSlot).getCardState();
        Message.obtain(mPhoneUT, EVENT_ICC_CHANGED, null).sendToTarget();
        Message.obtain(mPhoneUT, EVENT_UICC_APPS_ENABLEMENT_STATUS_CHANGED,
                new AsyncResult(null, true, null)).sendToTarget();
        processAllMessages();
        verify(mSubscriptionController, never()).getSubInfoForIccId(any());

        // Have IccId defined. But expected value and current value are the same. So no RIL command
        // should be sent.
        String iccId = "Fake iccId";
        doReturn(iccId).when(mUiccSlot).getIccId();
        Message.obtain(mPhoneUT, EVENT_ICC_CHANGED, null).sendToTarget();
        processAllMessages();
        verify(mSubscriptionController).getSubInfoForIccId(iccId);
        verify(mMockCi, never()).enableUiccApplications(anyBoolean(), any());
    }

    @Test
    @SmallTest
    public void testReapplyUiccApplicationEnablementSuccess() throws Exception {
        mPhoneUT.mCi = mMockCi;
        // Set SIM to be present, with a fake iccId, and notify enablement being false.
        doReturn(mUiccSlot).when(mUiccController).getUiccSlotForPhone(anyInt());
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mUiccSlot).getCardState();
        String iccId = "Fake iccId";
        doReturn(iccId).when(mUiccSlot).getIccId();
        Message.obtain(mPhoneUT, EVENT_UICC_APPS_ENABLEMENT_STATUS_CHANGED,
                new AsyncResult(null, false, null)).sendToTarget();
        processAllMessages();

        // Should try to enable uicc applications as by default hey are expected to be true.
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockCi, times(1)).enableUiccApplications(eq(true), messageCaptor.capture());
        // Send message back with no exception.
        AsyncResult.forMessage(messageCaptor.getValue(), null, null);
        messageCaptor.getValue().sendToTarget();
        processAllMessages();

        // There shouldn't be any retry message.
        moveTimeForward(5000);
        processAllMessages();
        verify(mMockCi, times(1)).enableUiccApplications(eq(true), any());
    }

    @Test
    @SmallTest
    public void testSetRadioPower() throws Exception {
        mPhoneUT.setRadioPower(false);
        verify(mSST).setRadioPowerForReason(false, false, false, false,
                Phone.RADIO_POWER_REASON_USER);

        // Turn on radio for emergency call.
        mPhoneUT.setRadioPower(true, true, false, true);
        verify(mSST).setRadioPowerForReason(true, true, false, true, Phone.RADIO_POWER_REASON_USER);
    }

    @Test
    @SmallTest
    public void testSetRadioPowerOnForTestEmergencyCall() {
        mPhoneUT.setRadioPower(false);
        verify(mSST).setRadioPowerForReason(false, false, false, false,
                Phone.RADIO_POWER_REASON_USER);

        mPhoneUT.setRadioPowerOnForTestEmergencyCall(false);
        verify(mSST).clearAllRadioOffReasons();
        verify(mSST).setRadioPowerForReason(eq(true), eq(false), anyBoolean(), eq(false),
                eq(Phone.RADIO_POWER_REASON_USER));
    }

    @Test
    @SmallTest
    public void testReapplyUiccApplicationEnablementRetry() throws Exception {
        mPhoneUT.mCi = mMockCi;
        // Set SIM to be present, with a fake iccId, and notify enablement being false.
        doReturn(mUiccSlot).when(mUiccController).getUiccSlotForPhone(anyInt());
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mUiccSlot).getCardState();
        String iccId = "Fake iccId";
        doReturn(iccId).when(mUiccSlot).getIccId();
        Message.obtain(mPhoneUT, EVENT_UICC_APPS_ENABLEMENT_STATUS_CHANGED,
                new AsyncResult(null, false, null)).sendToTarget();
        processAllMessages();

        // Should try to enable uicc applications as by default hey are expected to be true.
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockCi).enableUiccApplications(eq(true), messageCaptor.capture());
        clearInvocations(mMockCi);
        for (int i = 0; i < GsmCdmaPhone.ENABLE_UICC_APPS_MAX_RETRIES; i++) {
            // Send message back with SIM_BUSY exception. Should retry.
            AsyncResult.forMessage(messageCaptor.getValue(), null, new CommandException(
                    CommandException.Error.SIM_BUSY));
            messageCaptor.getValue().sendToTarget();
            processAllMessages();
            // There should be a retry message.
            moveTimeForward(5000);
            processAllMessages();
            verify(mMockCi).enableUiccApplications(eq(true), messageCaptor.capture());
            clearInvocations(mMockCi);
        }

        // Reaches max retries. Should NOT retry.
        AsyncResult.forMessage(messageCaptor.getValue(), null, new CommandException(
                CommandException.Error.SIM_BUSY));
        messageCaptor.getValue().sendToTarget();
        processAllMessages();
        // There should NOT be a retry message.
        moveTimeForward(5000);
        processAllMessages();
        verify(mMockCi, never()).enableUiccApplications(eq(true), messageCaptor.capture());
        clearInvocations(mMockCi);
    }

    @Test
    @SmallTest
    public void testSendUssdInService() throws Exception {
        PhoneInternalInterface.DialArgs dialArgs = new PhoneInternalInterface.DialArgs
                .Builder().setVideoState(VideoProfile.STATE_AUDIO_ONLY).build();

        setupTestSendUssd(dialArgs);

        // ServiceState is in service.
        doReturn(ServiceState.STATE_IN_SERVICE).when(mSST.mSS).getState();
        mPhoneUT.dial("*135#", dialArgs);
        verify(mMockCi).sendUSSD(eq("*135#"), any());
    }

    @Test
    @SmallTest
    public void testSendUssdInOutOfService() throws Exception {
        PhoneInternalInterface.DialArgs dialArgs = new PhoneInternalInterface.DialArgs
                .Builder().setVideoState(VideoProfile.STATE_AUDIO_ONLY).build();

        setupTestSendUssd(dialArgs);

        // ServiceState is out of service
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST.mSS)
                .getState(); /* CS out of service */
        doReturn(ServiceState.STATE_IN_SERVICE).when(mSST.mSS).getDataRegState();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_GSM).when(mSST.mSS)
                .getRilDataRadioTechnology(); /* PS not in LTE */
        mPhoneUT.dial("*135#", dialArgs);
        verify(mMockCi).sendUSSD(eq("*135#"), any());
    }

    @Test
    @SmallTest
    public void testSendUssdInAirplaneMode() throws Exception {
        PhoneInternalInterface.DialArgs dialArgs = new PhoneInternalInterface.DialArgs
                .Builder().setVideoState(VideoProfile.STATE_AUDIO_ONLY).build();

        setupTestSendUssd(dialArgs);

        // ServiceState is airplane mode.
        doReturn(ServiceState.STATE_POWER_OFF).when(mSST.mSS).getState(); /* CS POWER_OFF */
        mPhoneUT.dial("*135#", dialArgs);
        verify(mMockCi).sendUSSD(eq("*135#"), any());
    }

    private void setupTestSendUssd(PhoneInternalInterface.DialArgs dialArgs) throws Exception {
        mPhoneUT.mCi = mMockCi;
        ServiceState mImsServiceState = Mockito.mock(ServiceState.class);
        CallStateException callStateException = Mockito.mock(CallStateException.class);

        // Enable VoWiFi
        doReturn(true).when(mImsManager).isVolteEnabledByPlatform();
        doReturn(true).when(mImsManager).isEnhanced4gLteModeSettingEnabledByUser();
        doReturn(mImsServiceState).when(mImsPhone).getServiceState();
        doReturn(ServiceState.STATE_IN_SERVICE).when(mImsServiceState).getState();
        doReturn(true).when(mImsPhone).isWifiCallingEnabled();

        // Disable UT/XCAP
        doReturn(false).when(mImsPhone).isUtEnabled();

        // Throw CallStateException(Phone.CS_FALLBACK) from ImsPhone.dial().
        doReturn(Phone.CS_FALLBACK).when(callStateException).getMessage();
        doThrow(callStateException).when(mImsPhone).dial(eq("*135#"),
                any(PhoneInternalInterface.DialArgs.class));

        replaceInstance(Phone.class, "mImsPhone", mPhoneUT, mImsPhone);
    }

    @Test
    public void testEventCarrierConfigChanged() {
        mPhoneUT.mCi = mMockCi;
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(Phone.EVENT_CARRIER_CONFIG_CHANGED));
        processAllMessages();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockCi).getRadioCapability(captor.capture());
        assertEquals(captor.getValue().what, Phone.EVENT_GET_RADIO_CAPABILITY);
    }

    private void setupForWpsCallTest() throws Exception {
        mSST.mSS = mServiceState;
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();
        when(mImsPhone.getCallTracker()).thenReturn(mImsCT);
        mCT.mForegroundCall = mGsmCdmaCall;
        mCT.mBackgroundCall = mGsmCdmaCall;
        mCT.mRingingCall = mGsmCdmaCall;
        doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();
        replaceInstance(Phone.class, "mImsPhone", mPhoneUT, mImsPhone);
    }

    @Test
    public void testEventLceUpdate() {
        mPhoneUT.mCi = mMockCi;

        ArgumentCaptor<List<LinkCapacityEstimate>> captor = ArgumentCaptor.forClass(List.class);
        List<LinkCapacityEstimate> lceList1 = new ArrayList<>();
        lceList1.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_PRIMARY, 2000, 5000));
        lceList1.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_SECONDARY, 1000, 1500));

        List<LinkCapacityEstimate> lceList2 = new ArrayList<>();
        lceList2.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_COMBINED, 2000, 5000));

        List<LinkCapacityEstimate> lceList3 = new ArrayList<>();
        lceList3.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_COMBINED, 2000,
                LinkCapacityEstimate.INVALID));

        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_LINK_CAPACITY_CHANGED,
                new AsyncResult(null, lceList1, null)));
        processAllMessages();
        verify(mNotifier, times(1))
                .notifyLinkCapacityEstimateChanged(any(), captor.capture());
        assertEquals(2, captor.getValue().size());
        LinkCapacityEstimate lce1 = captor.getValue().get(1);
        assertEquals(1000, lce1.getDownlinkCapacityKbps());
        assertEquals(LinkCapacityEstimate.LCE_TYPE_SECONDARY, lce1.getType());

        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_LINK_CAPACITY_CHANGED,
                new AsyncResult(null, lceList2, null)));
        processAllMessages();
        verify(mNotifier, times(2))
                .notifyLinkCapacityEstimateChanged(any(), captor.capture());
        assertEquals(1, captor.getValue().size());

        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_LINK_CAPACITY_CHANGED,
                new AsyncResult(null, lceList3, null)));
        processAllMessages();
        verify(mNotifier, times(3))
                .notifyLinkCapacityEstimateChanged(any(), captor.capture());
        LinkCapacityEstimate lce3 = captor.getValue().get(0);
        assertEquals(LinkCapacityEstimate.INVALID, lce3.getUplinkCapacityKbps());
        assertEquals(LinkCapacityEstimate.LCE_TYPE_COMBINED, lce3.getType());
    }

    @Test
    @SmallTest
    public void testLoadAllowedNetworksFromSubscriptionDatabase_loadTheNullValue_isLoadedTrue() {
        int subId = 1;
        doReturn(subId).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());

        doReturn(null).when(mSubscriptionController).getSubscriptionProperty(anyInt(),
                eq(SubscriptionManager.ALLOWED_NETWORK_TYPES));

        mPhoneUT.loadAllowedNetworksFromSubscriptionDatabase();

        assertEquals(true,  mPhoneUT.isAllowedNetworkTypesLoadedFromDb());
    }

    @Test
    @SmallTest
    public void testLoadAllowedNetworksFromSubscriptionDatabase_subIdNotValid_isLoadedFalse() {
        int subId = -1;
        doReturn(subId).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());

        when(mSubscriptionController.getSubscriptionProperty(anyInt(),
                eq(SubscriptionManager.ALLOWED_NETWORK_TYPES))).thenReturn(null);


        mPhoneUT.loadAllowedNetworksFromSubscriptionDatabase();

        assertEquals(false, mPhoneUT.isAllowedNetworkTypesLoadedFromDb());
    }

    /**
     * Verifies that an emergency call placed on a SIM which does NOT explicitly define a number as
     * an emergency call will still be placed as an emergency call.
     * @throws CallStateException
     */
    @Test
    public void testEmergencyCallAnySim() throws CallStateException {
        setupEmergencyCallScenario(false /* USE_ONLY_DIALED_SIM_ECC_LIST */,
                false /* isEmergencyOnDialedSim */);

        ArgumentCaptor<PhoneInternalInterface.DialArgs> dialArgsArgumentCaptor =
                ArgumentCaptor.forClass(PhoneInternalInterface.DialArgs.class);
        mPhoneUT.dial(TEST_EMERGENCY_NUMBER, new ImsPhone.ImsDialArgs.Builder().build());

        // Should have dialed out over IMS and should have specified that it is an emergency call
        verify(mImsPhone).dial(anyString(), dialArgsArgumentCaptor.capture());
        PhoneInternalInterface.DialArgs args = dialArgsArgumentCaptor.getValue();
        assertTrue(args.isEmergency);
    }

    /**
     * Tests the scenario where a number is dialed on a sim where it is NOT an emergency number,
     * but it IS an emergency number based on {@link TelephonyManager#isEmergencyNumber(String)},
     * and the carrier wants to ONLY use the dialed SIM's ECC list.
     * @throws CallStateException
     */
    @Test
    public void testNotEmergencyNumberOnDialedSim1() throws CallStateException {
        setupEmergencyCallScenario(true /* USE_ONLY_DIALED_SIM_ECC_LIST */,
                false /* isEmergencyOnDialedSim */);

        ArgumentCaptor<PhoneInternalInterface.DialArgs> dialArgsArgumentCaptor =
                ArgumentCaptor.forClass(PhoneInternalInterface.DialArgs.class);
        mPhoneUT.dial(TEST_EMERGENCY_NUMBER, new ImsPhone.ImsDialArgs.Builder().build());

        // Should have dialed out over IMS and should have specified that it is NOT an emergency
        // call
        verify(mImsPhone).dial(anyString(), dialArgsArgumentCaptor.capture());
        PhoneInternalInterface.DialArgs args = dialArgsArgumentCaptor.getValue();
        assertFalse(args.isEmergency);
    }

    /**
     * Tests the scenario where a number is dialed on a sim where it is NOT an emergency number,
     * but it IS an emergency number based on {@link TelephonyManager#isEmergencyNumber(String)},
     * and the carrier wants to use the global ECC list.
     * @throws CallStateException
     */
    @Test
    public void testNotEmergencyNumberOnDialedSim2() throws CallStateException {
        setupEmergencyCallScenario(false /* USE_ONLY_DIALED_SIM_ECC_LIST */,
                false /* isEmergencyOnDialedSim */);

        ArgumentCaptor<PhoneInternalInterface.DialArgs> dialArgsArgumentCaptor =
                ArgumentCaptor.forClass(PhoneInternalInterface.DialArgs.class);
        mPhoneUT.dial(TEST_EMERGENCY_NUMBER, new ImsPhone.ImsDialArgs.Builder().build());

        // Should have dialed out over IMS and should have specified that it is an emergency call
        verify(mImsPhone).dial(anyString(), dialArgsArgumentCaptor.capture());
        PhoneInternalInterface.DialArgs args = dialArgsArgumentCaptor.getValue();
        assertTrue(args.isEmergency);
    }

    private void setupEmergencyCallScenario(boolean isUsingOnlyDialedSim,
            boolean isEmergencyPerDialedSim) {
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_USE_ONLY_DIALED_SIM_ECC_LIST_BOOL,
                isUsingOnlyDialedSim);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_USE_IMS_FIRST_FOR_EMERGENCY_BOOL, true);
        doReturn(true).when(mImsPhone).isImsAvailable();
        doReturn(true).when(mImsManager).isVolteEnabledByPlatform();
        doReturn(true).when(mImsManager).isEnhanced4gLteModeSettingEnabledByUser();
        doReturn(true).when(mImsManager).isNonTtyOrTtyOnVolteEnabled();
        doReturn(true).when(mImsPhone).isVoiceOverCellularImsEnabled();
        ServiceState ss = mock(ServiceState.class);
        doReturn(ServiceState.STATE_IN_SERVICE).when(ss).getState();
        doReturn(ss).when(mImsPhone).getServiceState();

        doReturn(true).when(mTelephonyManager).isEmergencyNumber(anyString());
        doReturn(isEmergencyPerDialedSim).when(mEmergencyNumberTracker).isEmergencyNumber(
                anyString(), anyBoolean());

        mPhoneUT.setImsPhone(mImsPhone);
    }
}
