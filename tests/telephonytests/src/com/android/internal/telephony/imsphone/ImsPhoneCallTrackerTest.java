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
package com.android.internal.telephony.imsphone;

import static android.net.NetworkStats.DEFAULT_NETWORK_YES;
import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_FOREGROUND;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;

import static com.android.testutils.NetworkStatsUtilsKt.assertNetworkStatsEquals;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.netstats.provider.INetworkStatsProviderCallback;
import android.os.Bundle;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSession;
import android.telephony.ims.ImsConferenceState;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.RtpHeaderExtensionType;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import com.android.ims.FeatureConnector;
import com.android.ims.ImsCall;
import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.internal.IImsCallSession;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.d2d.RtpTransport;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker.VtDataUsageProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Set;
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsPhoneCallTrackerTest extends TelephonyTest {
    private ImsPhoneCallTracker mCTUT;
    private MmTelFeature.Listener mMmTelListener;
    private FeatureConnector.Listener<ImsManager> mConnectorListener;
    private ImsMmTelManager.CapabilityCallback mCapabilityCallback;
    private ImsCall.Listener mImsCallListener;
    private ImsCall mImsCall;
    private ImsCall mSecondImsCall;
    private BroadcastReceiver mBroadcastReceiver;
    private Bundle mBundle = new Bundle();
    private static final int SUB_0 = 0;
    @Nullable private VtDataUsageProvider mVtDataUsageProvider;

    // Mocked classes
    private ArgumentCaptor<Set<RtpHeaderExtensionType>> mRtpHeaderExtensionTypeCaptor;
    private FeatureConnector<ImsManager> mMockConnector;
    private ImsCallSession mImsCallSession;
    private SharedPreferences mSharedPreferences;
    private ImsPhoneConnection.Listener mImsPhoneConnectionListener;
    private ImsConfig mImsConfig;
    private ImsPhoneConnection mImsPhoneConnection;
    private INetworkStatsProviderCallback mVtDataUsageProviderCb;
    private ImsPhoneCallTracker.ConnectorFactory mConnectorFactory;

    private final Executor mExecutor = Runnable::run;

    private void imsCallMocking(final ImsCall imsCall) throws Exception {

        doAnswer((Answer<Void>) invocation -> {
            // trigger the listener on accept call
            if (mImsCallListener != null) {
                mImsCallListener.onCallStarted(imsCall);
            }
            return null;
        }).when(imsCall).accept(anyInt());

        doAnswer((Answer<Void>) invocation -> {
            // trigger the listener on reject call
            int reasonCode = (int) invocation.getArguments()[0];
            if (mImsCallListener != null) {
                mImsCallListener.onCallStartFailed(imsCall, new ImsReasonInfo(reasonCode, -1));
                mImsCallListener.onCallTerminated(imsCall, new ImsReasonInfo(reasonCode, -1));
            }
            return null;
        }).when(imsCall).reject(anyInt());

        doAnswer((Answer<Void>) invocation -> {
            // trigger the listener on reject call
            int reasonCode = (int) invocation.getArguments()[0];
            if (mImsCallListener != null) {
                mImsCallListener.onCallTerminated(imsCall, new ImsReasonInfo(reasonCode, -1));
            }
            return null;
        }).when(imsCall).terminate(anyInt());

        doAnswer((Answer<Void>) invocation -> {
            if (mImsCallListener != null) {
                mImsCallListener.onCallHeld(imsCall);
            }
            return null;
        }).when(imsCall).hold();

        doReturn(mExecutor).when(mContext).getMainExecutor();
        imsCall.attachSession(mImsCallSession);
        doReturn("1").when(mImsCallSession).getCallId();
        doReturn(mImsCallProfile).when(mImsCallSession).getCallProfile();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mRtpHeaderExtensionTypeCaptor = ArgumentCaptor.forClass(Set.class);
        mMockConnector = mock(FeatureConnector.class);
        mImsCallSession = mock(ImsCallSession.class);
        mSharedPreferences = mock(SharedPreferences.class);
        mImsConfig = mock(ImsConfig.class);
        mVtDataUsageProviderCb = mock(INetworkStatsProviderCallback.class);
        mConnectorFactory = mock(ImsPhoneCallTracker.ConnectorFactory.class);
        mImsCallProfile.mCallExtras = mBundle;
        mImsCall = spy(new ImsCall(mContext, mImsCallProfile));
        mSecondImsCall = spy(new ImsCall(mContext, mImsCallProfile));
        mImsPhoneConnectionListener = mock(ImsPhoneConnection.Listener.class);
        mImsPhoneConnection = mock(ImsPhoneConnection.class);
        imsCallMocking(mImsCall);
        imsCallMocking(mSecondImsCall);
        doReturn(ImsFeature.STATE_READY).when(mImsManager).getImsServiceState();
        doReturn(mImsCallProfile).when(mImsManager).createCallProfile(anyInt(), anyInt());
        mContextFixture.addSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);

        doAnswer(invocation -> {
            mMmTelListener = (MmTelFeature.Listener) invocation.getArguments()[0];
            return null;
        }).when(mImsManager).open(any(), any(), any());

        doAnswer((Answer<ImsCall>) invocation -> {
            mImsCallListener =
                    (ImsCall.Listener) invocation.getArguments()[1];
            mImsCall.setListener(mImsCallListener);
            return mImsCall;
        }).when(mImsManager).takeCall(any(), any());

        doAnswer((Answer<ImsCall>) invocation -> {
            mImsCallListener =
                    (ImsCall.Listener) invocation.getArguments()[2];
            mSecondImsCall.setListener(mImsCallListener);
            return mSecondImsCall;
        }).when(mImsManager).makeCall(eq(mImsCallProfile), any(), any());

        doAnswer(invocation -> {
            mCapabilityCallback = (ImsMmTelManager.CapabilityCallback) invocation.getArguments()[0];
            return mCapabilityCallback;

        }).when(mImsManager).addCapabilitiesCallback(
                any(ImsMmTelManager.CapabilityCallback.class), any());

        doReturn(mImsConfig).when(mImsManager).getConfigInterface();

        doAnswer((Answer<FeatureConnector<ImsManager>>) invocation -> {
            mConnectorListener =
                    (FeatureConnector.Listener<ImsManager>) invocation.getArguments()[3];
            return mMockConnector;
        }).when(mConnectorFactory).create(any(), anyInt(), anyString(), any(), any());

        mCTUT = new ImsPhoneCallTracker(mImsPhone, mConnectorFactory, Runnable::run);
        mCTUT.setDataEnabled(true);

        final ArgumentCaptor<VtDataUsageProvider> vtDataUsageProviderCaptor =
                ArgumentCaptor.forClass(VtDataUsageProvider.class);
        verify(mStatsManager).registerNetworkStatsProvider(anyString(),
                vtDataUsageProviderCaptor.capture());
        mVtDataUsageProvider = vtDataUsageProviderCaptor.getValue();
        assertNotNull(mVtDataUsageProvider);
        mVtDataUsageProvider.setProviderCallbackBinder(mVtDataUsageProviderCb);
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), any());
        mBroadcastReceiver = receiverCaptor.getValue();
        assertNotNull(mBroadcastReceiver);

        logd("ImsPhoneCallTracker initiated");
        processAllMessages();

        verify(mMockConnector).connect();
        mConnectorListener.connectionReady(mImsManager, SUB_0);
    }

    @After
    public void tearDown() throws Exception {
        mCTUT = null;
        mMmTelListener = null;
        mConnectorListener = null;
        mCapabilityCallback = null;
        mImsCallListener = null;
        mImsCall = null;
        mSecondImsCall = null;
        mBroadcastReceiver = null;
        mBundle = null;
        mVtDataUsageProvider = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testVowifiDisabledOnLte() {
        // LTE is registered.
        doReturn(ImsRegistrationImplBase.REGISTRATION_TECH_LTE).when(
                mImsManager).getRegistrationTech();
        assertFalse(mCTUT.isVowifiEnabled());

        // enable Voice over LTE
        MmTelFeature.MmTelCapabilities caps = new MmTelFeature.MmTelCapabilities();
        caps.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        mCapabilityCallback.onCapabilitiesStatusChanged(caps);
        processAllMessages();

        // Voice over IWLAN is still disabled
        assertFalse(mCTUT.isVowifiEnabled());
    }

    @Test
    @SmallTest
    public void testVowifiDisabledOnIwlan() {
        // LTE is registered.
        doReturn(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).when(
                mImsManager).getRegistrationTech();
        assertFalse(mCTUT.isVowifiEnabled());

        // enable Voice over IWLAN
        MmTelFeature.MmTelCapabilities caps = new MmTelFeature.MmTelCapabilities();
        caps.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        mCapabilityCallback.onCapabilitiesStatusChanged(caps);
        processAllMessages();

        // Voice over IWLAN is enabled
        assertTrue(mCTUT.isVowifiEnabled());
    }

    @Test
    @SmallTest
    public void testImsFeatureCapabilityChange() {
        doReturn(ImsRegistrationImplBase.REGISTRATION_TECH_LTE).when(
                mImsManager).getRegistrationTech();
        assertFalse(mCTUT.isVoiceOverCellularImsEnabled());
        assertFalse(mCTUT.isVideoCallEnabled());

        // enable only Voice
        MmTelFeature.MmTelCapabilities caps = new MmTelFeature.MmTelCapabilities();
        caps.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        mCapabilityCallback.onCapabilitiesStatusChanged(caps);
        processAllMessages();

        assertTrue(mCTUT.isVoiceOverCellularImsEnabled());
        assertFalse(mCTUT.isVideoCallEnabled());
        // video call not enabled
        verify(mImsPhone, times(0)).notifyForVideoCapabilityChanged(anyBoolean());
        verify(mImsPhone, times(1)).onFeatureCapabilityChanged();

        // enable video call
        MmTelFeature.MmTelCapabilities capsVideo = new MmTelFeature.MmTelCapabilities();
        capsVideo.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        capsVideo.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
        mCapabilityCallback.onCapabilitiesStatusChanged(capsVideo);
        processAllMessages();
        assertTrue(mCTUT.isVideoCallEnabled());
        verify(mImsPhone, times(1)).notifyForVideoCapabilityChanged(eq(true));
    }

    @Test
    @SmallTest
    public void testCarrierConfigLoadSubscription() throws Exception {
        // Start with there being no subId loaded, so SubscriptionController#isActiveSubId is false
        // as part of setup, connectionReady is called, which ends up calling
        // updateCarrierConfiguration. Since the carrier config is not report carrier identified
        // config, we should not see updateImsServiceConfig called yet.
        verify(mImsManager, never()).updateImsServiceConfig();
        // Send disconnected indication
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);

        // Receive a subscription loaded and IMS connection ready indication.
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        sendCarrierConfigChanged();
        // CarrierConfigLoader has signalled that the carrier config has been applied for a specific
        // subscription. This will trigger unavailable -> ready indications.
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        verify(mImsManager).updateImsServiceConfig();
    }

    @Test
    @SmallTest
    public void testCarrierConfigSentLocked() throws Exception {
        // move to ImsService unavailable state.
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);

        sendCarrierConfigChanged();
        // No ImsService connected, so this will cache the config.
        verify(mImsManager, never()).updateImsServiceConfig();

        // Connect to ImsService, but sim is locked, so ensure we do not send configs yet
        doReturn(mIccCard).when(mPhone).getIccCard();
        doReturn(IccCardConstants.State.PIN_REQUIRED).when(mIccCard).getState();
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        verify(mImsManager, never()).updateImsServiceConfig();

        // Now move to ready and simulate carrier config change in response to SIM state change.
        doReturn(IccCardConstants.State.READY).when(mIccCard).getState();
        sendCarrierConfigChanged();
        verify(mImsManager).updateImsServiceConfig();
    }

    @Test
    @SmallTest
    public void testCarrierConfigSentAfterReady() throws Exception {
        verify(mImsManager, never()).updateImsServiceConfig();

        // Receive a subscription loaded and IMS connection ready indication.
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        // CarrierConfigLoader has signalled that the carrier config has been applied for a specific
        // subscription. This will trigger unavailable -> ready indications.
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        // Did not receive carrier config changed yet
        verify(mImsManager, never()).updateImsServiceConfig();
        sendCarrierConfigChanged();
        processAllMessages();
        verify(mImsManager).updateImsServiceConfig();
    }

    @Test
    @SmallTest
    public void testCarrierConfigSentBeforeReady() throws Exception {
        // move to ImsService unavailable state.
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);

        sendCarrierConfigChanged();
        // No ImsService connected, so this will cache the config.
        verify(mImsManager, never()).updateImsServiceConfig();

        // Connect to ImsService and ensure that the pending carrier config change is processed
        // properly.
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        verify(mImsManager).updateImsServiceConfig();
    }

    @Test
    @SmallTest
    public void testCarrierConfigSentAfterReadyAndCrash() throws Exception {
        verify(mImsManager, never()).updateImsServiceConfig();

        // Receive a subscription loaded and IMS connection ready indication.
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        // CarrierConfigLoader has signalled that the carrier config has been applied for a specific
        // subscription. This will trigger unavailable -> ready indications.
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        // Did not receive carrier config changed yet
        verify(mImsManager, never()).updateImsServiceConfig();
        sendCarrierConfigChanged();
        processAllMessages();
        // ImsService crashes and reconnects
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        verify(mImsManager, times(2)).updateImsServiceConfig();
    }

    @Test
    @SmallTest
    public void testCarrierConfigSentBeforeReadyAndCrash() throws Exception {
        // move to ImsService unavailable state.
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);

        sendCarrierConfigChanged();
        // No ImsService connected, so this will cache the config.
        verify(mImsManager, never()).updateImsServiceConfig();

        // Connect to ImsService and then simulate a crash recovery. We should make sure that the
        // configs are sent again after recovery.
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        processAllMessages();
        verify(mImsManager, times(2)).updateImsServiceConfig();
    }

    @Test
    @SmallTest
    public void testImsMTCall() {
        ImsPhoneConnection connection = setupRingingConnection();
        assertEquals(android.telecom.Connection.VERIFICATION_STATUS_PASSED,
                connection.getNumberVerificationStatus());
    }

    @Test
    @SmallTest
    public void testImsMTCallMissed() {
        ImsPhoneConnection connection = setupRingingConnection();
        mImsCallListener.onCallTerminated(connection.getImsCall(),
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));
        assertEquals(DisconnectCause.INCOMING_MISSED, connection.getDisconnectCause());
    }

    @Test
    @SmallTest
    public void testImsMTCallRejected() {
        ImsPhoneConnection connection = setupRingingConnection();
        connection.onHangupLocal();
        mImsCallListener.onCallTerminated(connection.getImsCall(),
                new ImsReasonInfo(ImsReasonInfo.CODE_SIP_REQUEST_TIMEOUT, 0));
        assertEquals(DisconnectCause.INCOMING_REJECTED, connection.getDisconnectCause());
    }

    @Test
    @SmallTest
    public void testRejectedElsewhereIsRejected() {
        ImsPhoneConnection connection = setupRingingConnection();
        mImsCallListener.onCallTerminated(connection.getImsCall(),
                new ImsReasonInfo(ImsReasonInfo.CODE_REJECTED_ELSEWHERE, 0));
        assertEquals(DisconnectCause.INCOMING_REJECTED, connection.getDisconnectCause());
    }

    @Test
    @SmallTest
    public void testRemoteCallDeclineIsRejected() {
        ImsPhoneConnection connection = setupRingingConnection();
        mImsCallListener.onCallTerminated(connection.getImsCall(),
                new ImsReasonInfo(ImsReasonInfo.CODE_REMOTE_CALL_DECLINE, 0));
        assertEquals(DisconnectCause.INCOMING_REJECTED, connection.getDisconnectCause());
    }

    private ImsPhoneConnection setupRingingConnection() {
        mImsCallProfile.setCallerNumberVerificationStatus(
                ImsCallProfile.VERIFICATION_STATUS_PASSED);
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        // mock a MT call
        mMmTelListener.onIncomingCall(mock(IImsCallSession.class), Bundle.EMPTY);
        verify(mImsPhone, times(1)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(1)).notifyIncomingRing();
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());
        assertTrue(mCTUT.mRingingCall.isRinging());
        assertEquals(1, mCTUT.mRingingCall.getConnections().size());
        ImsPhoneConnection connection =
                (ImsPhoneConnection) mCTUT.mRingingCall.getConnections().get(0);
        connection.addListener(mImsPhoneConnectionListener);
        assertEquals(android.telecom.Connection.VERIFICATION_STATUS_PASSED,
                connection.getNumberVerificationStatus());
        return connection;
    }

    @Test
    @SmallTest
    public void testImsMTCallAccept() {
        testImsMTCall();
        assertTrue(mCTUT.mRingingCall.isRinging());
        try {
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
            verify(mImsCall, times(1)).accept(eq(ImsCallProfile
                    .getCallTypeFromVideoState(ImsCallProfile.CALL_TYPE_VOICE)));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(1, mCTUT.mForegroundCall.getConnections().size());
    }

    @Test
    @SmallTest
    public void testImsCepOnPeer() throws Exception {
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SUPPORT_IMS_CONFERENCE_EVENT_PACKAGE_ON_PEER_BOOL, true);
        mCTUT.updateCarrierConfigCache(bundle);
        testImsMTCallAccept();
        doReturn(false).when(mImsCall).isConferenceHost();
        doReturn(true).when(mImsCall).isMultiparty();

        injectConferenceState();

        verify(mImsPhoneConnectionListener).onConferenceParticipantsChanged(any());
    }

    @Test
    @SmallTest
    public void testImsNoCepOnPeer() throws Exception {
        mCTUT.setSupportCepOnPeer(false);

        testImsMTCallAccept();
        doReturn(false).when(mImsCall).isConferenceHost();
        doReturn(true).when(mImsCall).isMultiparty();

        injectConferenceState();

        verify(mImsPhoneConnectionListener, never()).onConferenceParticipantsChanged(any());
    }

    private void injectConferenceState() {
        ImsPhoneConnection connection = mCTUT.getConnections().get(0);
        connection.addListener(mImsPhoneConnectionListener);

        ImsConferenceState state = new ImsConferenceState();
        // Yuck
        Bundle participant = new Bundle();
        participant.putString(ImsConferenceState.USER, "sip:6505551212@fakeims.com");
        participant.putString(ImsConferenceState.DISPLAY_TEXT, "yuck");
        participant.putString(ImsConferenceState.ENDPOINT, "sip:6505551212@fakeims.com");
        participant.putString(ImsConferenceState.STATUS, "connected");
        state.mParticipants.put("sip:6505551212@fakeims.com", participant);

        mImsCall.conferenceStateUpdated(state);
    }

    @Test
    @SmallTest
    public void testImsHoldException() throws Exception {
        testImsMTCallAccept();
        doThrow(new ImsException()).when(mImsCall).hold();
        try {
            mCTUT.holdActiveCall();
            Assert.fail("No exception thrown");
        } catch (Exception e) {
            // expected
            verify(mImsCall).hold();
        }

        // After the first hold exception, try holding (successfully) again to make sure that it
        // goes through
        doNothing().when(mImsCall).hold();
        try {
            mCTUT.holdActiveCall();
            verify(mImsCall, times(2)).hold();
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
    }

    @Test
    @SmallTest
    public void testImsMTCallReject() {
        testImsMTCall();
        assertTrue(mCTUT.mRingingCall.isRinging());
        try {
            mCTUT.rejectCall();
            verify(mImsCall, times(1)).reject(eq(ImsReasonInfo.CODE_USER_DECLINE));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(0, mCTUT.mRingingCall.getConnections().size());
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
    }

    @Test
    @SmallTest
    public void testImsMTCallAcceptHangUp() {
        testImsMTCallAccept();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        try {
            mCTUT.hangup(mCTUT.mForegroundCall);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
    }

    @Test
    @SmallTest
    public void testImsMTCallAcceptHold() {
        testImsMTCallAccept();

        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        // mock a new MT
        try {
            doReturn(mSecondImsCall).when(mImsManager).takeCall(any(IImsCallSession.class),
                    any(ImsCall.Listener.class));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        mMmTelListener.onIncomingCall(mock(IImsCallSession.class), Bundle.EMPTY);

        verify(mImsPhone, times(2)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(2)).notifyIncomingRing();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(ImsPhoneCall.State.WAITING, mCTUT.mRingingCall.getState());
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());

        //hold the foreground active call, accept the new ringing call
        try {
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
            verify(mImsCall, times(1)).hold();
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }

        processAllMessages();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(Call.State.HOLDING, mCTUT.mBackgroundCall.getState());
    }

    @Test
    @SmallTest
    public void testImsMTActiveHoldServiceDisconnect() {
        testImsMTCallAccept();

        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        // mock a new MT
        try {
            doReturn(mSecondImsCall).when(mImsManager).takeCall(any(IImsCallSession.class),
                    any(ImsCall.Listener.class));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        mMmTelListener.onIncomingCall(mock(IImsCallSession.class), Bundle.EMPTY);

        verify(mImsPhone, times(2)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(2)).notifyIncomingRing();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(ImsPhoneCall.State.WAITING, mCTUT.mRingingCall.getState());
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());

        //hold the foreground active call, accept the new ringing call
        try {
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
            verify(mImsCall, times(1)).hold();
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }

        processAllMessages();
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        assertEquals(Call.State.HOLDING, mCTUT.mBackgroundCall.getState());

        // Now fake the ImsService crashing
        mCTUT.hangupAllOrphanedConnections(DisconnectCause.LOST_SIGNAL);
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        try {
            // ensure new calls are not blocked by any lingering state after crash.
            mCTUT.checkForDialIssues();
        } catch (CallStateException e) {
            fail("checkForDialIssues should not generate a CallStateException: " + e.getMessage());
        }
    }

    /**
     * Ensures that the dial method will perform a shared preferences lookup using the correct
     * shared preference key to determine the CLIR mode.
     */
    @Test
    @SmallTest
    public void testDialClirMode() {
        mCTUT.setSharedPreferenceProxy((Context context) -> {
            return mSharedPreferences;
        });
        ArgumentCaptor<String> mStringCaptor = ArgumentCaptor.forClass(String.class);
        doReturn(CommandsInterface.CLIR_INVOCATION).when(mSharedPreferences).getInt(
                mStringCaptor.capture(), anyInt());

        try {
            mCTUT.dial("+17005554141", VideoProfile.STATE_AUDIO_ONLY, null);
        } catch (CallStateException cse) {
            cse.printStackTrace();
            Assert.fail("unexpected exception thrown" + cse.getMessage());
        }

        // Ensure that the correct key was queried from the shared prefs.
        assertEquals("clir_sub_key0", mStringCaptor.getValue());
    }

    /**
     * Ensures for an emergency call that the dial method will default the CLIR to
     * {@link CommandsInterface#CLIR_SUPPRESSION}, ensuring the caller's ID is shown.
     */
    @Test
    @SmallTest
    public void testEmergencyDialSuppressClir() {
        String dialString = "+17005554141";
        mCTUT.setSharedPreferenceProxy((Context context) -> {
            return mSharedPreferences;
        });

        doReturn(true).when(mTelephonyManager).isEmergencyNumber(dialString);

        // Set preference to hide caller ID.
        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        doReturn(CommandsInterface.CLIR_INVOCATION).when(mSharedPreferences).getInt(
                stringCaptor.capture(), anyInt());

        try {
            mCTUT.dial(dialString, new ImsPhone.ImsDialArgs.Builder().setIsEmergency(true).build());

            ArgumentCaptor<ImsCallProfile> profileCaptor = ArgumentCaptor.forClass(
                    ImsCallProfile.class);
            verify(mImsManager, times(1)).makeCall(eq(mImsCallProfile),
                    eq(new String[]{dialString}), any());

            // Because this is an emergency call, we expect caller id to be visible now.
            assertEquals(mImsCallProfile.getCallExtraInt(ImsCallProfile.EXTRA_OIR),
                    CommandsInterface.CLIR_SUPPRESSION);
        } catch (CallStateException cse) {
            cse.printStackTrace();
            Assert.fail("unexpected exception thrown" + cse.getMessage());
        } catch (ImsException ie) {
            ie.printStackTrace();
            Assert.fail("unexpected exception thrown" + ie.getMessage());
        }
    }

    @Test
    @SmallTest
    public void testImsMOCallDial() {
        startOutgoingCall();
        //call established
        mImsCallListener.onCallProgressing(mSecondImsCall);
        processAllMessages();
        assertEquals(Call.State.ALERTING, mCTUT.mForegroundCall.getState());
    }

    @Test
    @SmallTest
    public void testImsMoCallCrash() {
        startOutgoingCall();
        // Now fake the ImsService crashing
        mCTUT.hangupAllOrphanedConnections(DisconnectCause.LOST_SIGNAL);
        processAllMessages();
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        try {
            // ensure new calls are not blocked by any lingering state after crash.
            mCTUT.checkForDialIssues();
        } catch (CallStateException e) {
            fail("checkForDialIssues should not generate a CallStateException: " + e.getMessage());
        }
    }

    private void startOutgoingCall() {
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());

        try {
            mCTUT.dial("+17005554141", ImsCallProfile.CALL_TYPE_VOICE, null);
            verify(mImsManager, times(1)).makeCall(eq(mImsCallProfile),
                    eq(new String[]{"+17005554141"}), (ImsCall.Listener) any());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        processAllMessages();
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());
        assertEquals(Call.State.DIALING, mCTUT.mForegroundCall.getState());
    }

    @FlakyTest
    @Ignore
    @Test
    @SmallTest
    public void testImsMTActiveMODial() {
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
        assertEquals(Call.State.IDLE, mCTUT.mBackgroundCall.getState());

        testImsMTCallAccept();

        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        assertEquals(Call.State.IDLE, mCTUT.mBackgroundCall.getState());
        try {
            mCTUT.dial("+17005554141", ImsCallProfile.CALL_TYPE_VOICE, null);
            verify(mImsManager, times(1)).makeCall(eq(mImsCallProfile),
                    eq(new String[]{"+17005554141"}), (ImsCall.Listener) any());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        processAllMessages();
        assertEquals(Call.State.DIALING, mCTUT.mForegroundCall.getState());
        assertEquals(Call.State.HOLDING, mCTUT.mBackgroundCall.getState());
    }

    @Test
    @SmallTest
    public void testImsMOCallHangup() {
        testImsMOCallDial();
        //hangup before call go to active
        try {
            mCTUT.hangup(mCTUT.mForegroundCall);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
    }

    @Test
    @SmallTest
    public void testImsSendDtmf() {
        //establish a MT call
        testImsMTCallAccept();
        mCTUT.sendDtmf(PhoneNumberUtils.PAUSE, null);
        //verify trigger sendDtmf to mImsCall
        verify(mImsCall, times(1)).sendDtmf(eq(PhoneNumberUtils.PAUSE), (Message) isNull());
        // mock a new MT
        try {
            doReturn(mSecondImsCall).when(mImsManager).takeCall(any(IImsCallSession.class),
                    any(ImsCall.Listener.class));
            mMmTelListener.onIncomingCall(mock(IImsCallSession.class), Bundle.EMPTY);
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }

        processAllMessages();

        mCTUT.sendDtmf(PhoneNumberUtils.WAIT, null);
        //verify trigger sendDtmf to mImsSecondCall
        verify(mSecondImsCall, times(1)).sendDtmf(eq(PhoneNumberUtils.WAIT), (Message) isNull());
    }

    @Test
    @SmallTest
    public void testReasonCodeRemap() {
        loadReasonCodeRemap();

        assertEquals(ImsReasonInfo.CODE_WIFI_LOST, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(1, 1, "Wifi signal lost.")));
        assertEquals(ImsReasonInfo.CODE_WIFI_LOST, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(200, 1, "Wifi signal lost.")));
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE,
                mCTUT.maybeRemapReasonCode(new ImsReasonInfo(501, 1, "Call answered elsewhere.")));
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE,
                mCTUT.maybeRemapReasonCode(new ImsReasonInfo(501, 1, "CALL answered elsewhere.")));
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE,
                mCTUT.maybeRemapReasonCode(new ImsReasonInfo(510, 1, "Call answered elsewhere.")));
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE,
                mCTUT.maybeRemapReasonCode(new ImsReasonInfo(510, 1, "CALL ANswered elsewhere.")));
        assertEquals(90210, mCTUT.maybeRemapReasonCode(new ImsReasonInfo(90210, 1,
                "Call answered elsewhere.")));
    }

    private void clearCarrierConfig() {
        PersistableBundle bundle = new PersistableBundle();
        mCTUT.updateCarrierConfigCache(bundle);
    }

    private void loadReasonCodeRemap() {
        mCTUT.addReasonCodeRemapping(null, "Wifi signal lost.", ImsReasonInfo.CODE_WIFI_LOST);
        mCTUT.addReasonCodeRemapping(501, "Call answered elsewhere.",
                ImsReasonInfo.CODE_ANSWERED_ELSEWHERE);
        mCTUT.addReasonCodeRemapping(510, "Call answered elsewhere.",
                ImsReasonInfo.CODE_ANSWERED_ELSEWHERE);
        mCTUT.addReasonCodeRemapping(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, "",
                ImsReasonInfo.CODE_SIP_FORBIDDEN);
        mCTUT.addReasonCodeRemapping(ImsReasonInfo.CODE_SIP_SERVICE_UNAVAILABLE,
                "emergency calls over wifi not allowed in this location",
                ImsReasonInfo.CODE_EMERGENCY_CALL_OVER_WFC_NOT_AVAILABLE);
        mCTUT.addReasonCodeRemapping(ImsReasonInfo.CODE_SIP_FORBIDDEN,
                "service not allowed in this location",
                ImsReasonInfo.CODE_WFC_SERVICE_NOT_AVAILABLE_IN_THIS_LOCATION);
    }

    private void loadReasonCodeRemapCarrierConfig() {
        PersistableBundle bundle = new PersistableBundle();
        String[] mappings = new String[] {
                // These shall be equivalent to the remappings added in setUp():
                "*|Wifi signal lost.|1407",
                "501|Call answered elsewhere.|1014",
                "510|Call answered elsewhere.|1014",
                "510||332",
                "352|emergency calls over wifi not allowed in this location|1622",
                "332|service not allowed in this location|1623",
                };
        bundle.putStringArray(CarrierConfigManager.KEY_IMS_REASONINFO_MAPPING_STRING_ARRAY,
                mappings);
        mCTUT.updateCarrierConfigCache(bundle);
    }

    @Test
    @SmallTest
    public void testReasonCodeRemapCarrierConfig() {
        clearCarrierConfig();
        // The map shall become empty now

        assertEquals(510, // ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE
                mCTUT.maybeRemapReasonCode(new ImsReasonInfo(510, 1, "Call answered elsewhere.")));

        loadReasonCodeRemapCarrierConfig();
        testReasonCodeRemap();
        testNumericOnlyRemap();
        testRemapEmergencyCallsOverWfc();
        testRemapWfcNotAvailable();
    }

    private void loadReasonCodeRemapCarrierConfigWithWildcardMessage() {
        PersistableBundle bundle = new PersistableBundle();
        String[] mappings = new String[]{
                "1014|call completed elsewhere|1014",
                "1014|Call Rejected By User|510",
                "1014|*|510",
                "510|Call completed elsewhere|1014",
                };
        bundle.putStringArray(CarrierConfigManager.KEY_IMS_REASONINFO_MAPPING_STRING_ARRAY,
                mappings);
        mCTUT.updateCarrierConfigCache(bundle);
    }

    @Test
    @SmallTest
    public void testReasonCodeRemapCarrierConfigWithWildcardMessage() {
        clearCarrierConfig();
        // The map shall become empty now

        loadReasonCodeRemapCarrierConfigWithWildcardMessage();
        assertEquals(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(1014, 200, "Call Rejected By User"))); // 1014 -> 510
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(1014, 200, "Call completed elsewhere"))); // 1014 -> 1014
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(510, 200,
                        "Call completed elsewhere by instance urn:gsma:imei:xxx"))); // 510 -> 1014

        // Simulate that after SIM swap the new carrier config doesn't have the mapping for 1014
        loadReasonCodeRemapCarrierConfig();
        assertEquals(ImsReasonInfo.CODE_ANSWERED_ELSEWHERE, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(1014, 200, "Call Rejected By User"))); // 1014 -> 1014
    }

    @Test
    @SmallTest
    public void testDialImsServiceUnavailable() throws ImsException {
        doThrow(new ImsException("Test Exception", ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN)).when(
                mImsManager).createCallProfile(anyInt(), anyInt());
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());

        try {
            mCTUT.dial("+17005554141", ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception e) {
            Assert.fail();
        }

        processAllMessages();

        // Simulate ImsManager getting reconnected.
        mConnectorListener.connectionReady(mImsManager, SUB_0);
        verify(mImsManager, never()).makeCall(nullable(ImsCallProfile.class),
                eq(new String[]{"+17005554141"}), nullable(ImsCall.Listener.class));
        // Make sure that open is called in ImsPhoneCallTracker when it was first connected and
        // again after retry.
        verify(mImsManager, times(2)).open(any(), any(), any());
    }

    @FlakyTest
    @Ignore
    @Test
    @SmallTest
    public void testTTYImsServiceUnavailable() throws ImsException {
        doThrow(new ImsException("Test Exception", ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN)).when(
                mImsManager).setUiTTYMode(nullable(Context.class), anyInt(),
                nullable(Message.class));

        mCTUT.setUiTTYMode(0, new Message());

        processAllMessages();
        // Make sure that open is called in ImsPhoneCallTracker to re-establish connection to
        // ImsService
        verify(mImsManager, times(2)).open(any(), any(), any());
    }

    @Test
    @SmallTest
    public void testRewriteOutgoingNumber() {
        try {
            doAnswer(new Answer<ImsCall>() {
                @Override
                public ImsCall answer(InvocationOnMock invocation) throws Throwable {
                    mImsCallListener =
                            (ImsCall.Listener) invocation.getArguments()[2];
                    ImsCall imsCall = spy(new ImsCall(mContext, mImsCallProfile));
                    imsCall.setListener(mImsCallListener);
                    imsCallMocking(imsCall);
                    return imsCall;
                }
            }).when(mImsManager).makeCall(eq(mImsCallProfile), (String[]) any(),
                    (ImsCall.Listener) any());
        } catch (ImsException ie) {
        }

        // Perform a dial string remapping.
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
        bundle.putStringArray(CarrierConfigManager.KEY_DIAL_STRING_REPLACE_STRING_ARRAY,
                new String[] {"*55:6505551212"});

        ImsPhoneConnection connection = null;
        try {
            connection = (ImsPhoneConnection) mCTUT.dial("*55",
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection == null) {
            Assert.fail("connection is null");
        }
        Assert.assertEquals("6505551212", connection.getConvertedNumber());
        Assert.assertEquals("*55", connection.getAddress());
    }


    /**
     * Tests carrier requirement to re-map certain dialstrings based on the phones service state.
     * Dial strings in a particular roaming state (ex. ROAMING_TYPE_INTERNATIONAL) can be mapped
     * to the number.  Ideally, dialstrings in different roaming states will be mapped to
     * different remappings.
     *
     * ex.
     *
     * dialstring --> remapping
     *
     * 611 --> 123 , *611 --> 123   when  ServiceState.ROAMING_TYPE_DOMESTIC
     *
     * 611 --> 456 , *611 --> 456   when  ServiceState.ROAMING_TYPE_INTERNATIONAL
     */
    @Test
    @MediumTest
    public void testRewriteOutgoingNumberBasedOnRoamingState() {
        // mock carrier [dialstring]:[remapping]
        final String dialString = "611";
        final String dialStringStar = "*611";
        final String remapping1 = "1111111111";
        final String remapping2 = "2222222222";

        // Create the re-mappings by getting the mock carrier bundle and inserting string arrays
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
        // insert domestic roaming bundle
        bundle.putStringArray(CarrierConfigManager
                        .KEY_DIAL_STRING_REPLACE_STRING_ARRAY,
                new String[]{(dialString + ":" + remapping1),
                        (dialStringStar + ":" + remapping1)});
        // insert international roaming bundle
        bundle.putStringArray(CarrierConfigManager
                        .KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY,
                new String[]{(dialString + ":" + remapping2),
                        (dialStringStar + ":" + remapping2)});

        try {
            doAnswer(new Answer<ImsCall>() {
                @Override
                public ImsCall answer(InvocationOnMock invocation) throws Throwable {
                    mImsCallListener =
                            (ImsCall.Listener) invocation.getArguments()[2];
                    ImsCall imsCall = spy(new ImsCall(mContext, mImsCallProfile));
                    imsCall.setListener(mImsCallListener);
                    imsCallMocking(imsCall);
                    return imsCall;
                }
            }).when(mImsManager).makeCall(eq(mImsCallProfile), (String[]) any(),
                    (ImsCall.Listener) any());
        } catch (ImsException ie) {
        }

        // set mock call for helper function CallTracker#shouldPerformInternationalNumberRemapping
        doReturn(ServiceState.ROAMING_TYPE_INTERNATIONAL)
                .when(mServiceState).getVoiceRoamingType();

        // perform a call while service is state in roaming international
        ImsPhoneConnection connection = null;
        try {
            connection = (ImsPhoneConnection) mCTUT.dial(dialString,
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection == null) {
            Assert.fail("connection is null");
        }

        Assert.assertEquals(dialString, connection.getAddress());
        Assert.assertEquals(remapping2, connection.getConvertedNumber());

        mCTUT.hangupAllOrphanedConnections(DisconnectCause.NORMAL);

        // perform a 2nd call while service state is in roaming international
        ImsPhoneConnection connection2 = null;
        try {
            connection2 = (ImsPhoneConnection) mCTUT.dial(dialStringStar,
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection2 == null) {
            Assert.fail("connection is null");
        }

        Assert.assertEquals(dialStringStar, connection2.getAddress());
        Assert.assertEquals(remapping2, connection2.getConvertedNumber());

        mCTUT.hangupAllOrphanedConnections(DisconnectCause.NORMAL);


        // CHANGE THE SERVICE STATE: international --> domestic
        doReturn(ServiceState.ROAMING_TYPE_DOMESTIC)
                .when(mServiceState).getVoiceRoamingType();

        // perform 3rd call while service state is in roaming DOMESTIC
        ImsPhoneConnection connection3 = null;
        try {
            connection3 = (ImsPhoneConnection) mCTUT.dial(dialString,
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection3 == null) {
            Assert.fail("connection is null");
        }

        Assert.assertEquals(dialString, connection3.getAddress());
        Assert.assertEquals(remapping1, connection3.getConvertedNumber());


        mCTUT.hangupAllOrphanedConnections(DisconnectCause.NORMAL);

        // perform 4th call while service state is in roaming DOMESTIC
        ImsPhoneConnection connection4 = null;
        try {
            connection4 = (ImsPhoneConnection) mCTUT.dial(dialStringStar,
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection4 == null) {
            Assert.fail("connection is null");
        }

        Assert.assertEquals(dialStringStar, connection4.getAddress());
        Assert.assertEquals(remapping1, connection4.getConvertedNumber());

        mCTUT.hangupAllOrphanedConnections(DisconnectCause.NORMAL);
    }


    /**
     * Tests the edge case where the phone is in ServiceState.ROAMING_TYPE_INTERNATIONAL but the
     * Carrier never set the bundle for this ServiceState.  Always default to
     * CarrierConfigManager.KEY_DIAL_STRING_REPLACE_STRING_ARRAY.
     */
    @Test
    @SmallTest
    public void testRewriteOutgoingNumberInternationalButBundleNotSet() {
        // mock carrier [dialstring]:[remapping]
        final String dialString = "611";
        final String dialStringStar = "*611";
        final String remapping1 = "1111111111";

        // Create the re-mappings by getting the mock carrier bundle and inserting string arrays
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
        // insert domestic roaming bundle
        bundle.putStringArray(CarrierConfigManager
                        .KEY_DIAL_STRING_REPLACE_STRING_ARRAY,
                new String[]{(dialString + ":" + remapping1),
                        (dialStringStar + ":" + remapping1)});

        try {
            doAnswer(new Answer<ImsCall>() {
                @Override
                public ImsCall answer(InvocationOnMock invocation) throws Throwable {
                    mImsCallListener =
                            (ImsCall.Listener) invocation.getArguments()[2];
                    ImsCall imsCall = spy(new ImsCall(mContext, mImsCallProfile));
                    imsCall.setListener(mImsCallListener);
                    imsCallMocking(imsCall);
                    return imsCall;
                }
            }).when(mImsManager).makeCall(eq(mImsCallProfile), (String[]) any(),
                    (ImsCall.Listener) any());
        } catch (ImsException ie) {
        }

        doReturn(ServiceState.ROAMING_TYPE_INTERNATIONAL)
                .when(mServiceState).getVoiceRoamingType();

        Assert.assertNotNull(mImsPhone);
        Assert.assertNotNull(mImsPhone.getDefaultPhone());

        ImsPhoneConnection connection = null;
        try {
            connection = (ImsPhoneConnection) mCTUT.dial(dialString,
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection == null) {
            Assert.fail("connection is null");
        }

        // helper function CallTracker#shouldPerformInternationalNumberRemapping early exists since
        // the KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY bundle is null. Therefore,
        // we should never check the service state and default to
        // KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY bundle
        verify(mServiceState, times(0)).getVoiceRoamingType();

        Assert.assertEquals(mImsPhone.getDefaultPhone().getServiceState().getVoiceRoamingType(),
                ServiceState.ROAMING_TYPE_INTERNATIONAL);

        Assert.assertNull(bundle.getStringArray(CarrierConfigManager
                .KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY));

        Assert.assertEquals(dialString, connection.getAddress());
        Assert.assertEquals(remapping1, connection.getConvertedNumber());

        mCTUT.hangupAllOrphanedConnections(DisconnectCause.NORMAL);
    }

    /**
     * Test notification of handover from LTE to WIFI and WIFI to LTE and ensure that the expected
     * connection events are sent.
     */
    @Test
    @SmallTest
    public void testNotifyHandovers() {
        setupCarrierConfig();

        //establish a MT call
        testImsMTCallAccept();
        ImsPhoneConnection connection =
                (ImsPhoneConnection) mCTUT.mForegroundCall.getConnections().get(0);
        ImsCall call = connection.getImsCall();
        // Needs to be a video call to see this signalling.
        mImsCallProfile.mCallType = ImsCallProfile.CALL_TYPE_VT;

        // First handover from LTE to WIFI; this takes us into a mid-call state.
        call.getImsCallSessionListenerProxy().callSessionHandover(call.getCallSession(),
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN,
                new ImsReasonInfo());
        // Handover back to LTE.
        call.getImsCallSessionListenerProxy().callSessionHandover(call.getCallSession(),
                TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyManager.NETWORK_TYPE_LTE,
                new ImsReasonInfo());
        verify(mImsPhoneConnectionListener).onConnectionEvent(eq(
                TelephonyManager.EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE), isNull());

        // Finally hand back to WIFI
        call.getImsCallSessionListenerProxy().callSessionHandover(call.getCallSession(),
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN,
                new ImsReasonInfo());
        verify(mImsPhoneConnectionListener).onConnectionEvent(eq(
                TelephonyManager.EVENT_HANDOVER_VIDEO_FROM_LTE_TO_WIFI), isNull());
    }

    /**
     * Configure carrier config options relevant to the unit test.
     */
    public void setupCarrierConfig() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_NOTIFY_HANDOVER_VIDEO_FROM_LTE_TO_WIFI_BOOL,
                true);
        bundle.putBoolean(CarrierConfigManager.KEY_NOTIFY_HANDOVER_VIDEO_FROM_WIFI_TO_LTE_BOOL,
                true);
        bundle.putBoolean(CarrierConfigManager.KEY_NOTIFY_VT_HANDOVER_TO_WIFI_FAILURE_BOOL, true);
        mCTUT.updateCarrierConfigCache(bundle);
    }

    @Test
    @SmallTest
    public void testLowBatteryDisconnectMidCall() {
        assertEquals(DisconnectCause.LOW_BATTERY, mCTUT.getDisconnectCauseFromReasonInfo(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_LOW_BATTERY, 0), Call.State.ACTIVE));
        assertEquals(DisconnectCause.LOW_BATTERY, mCTUT.getDisconnectCauseFromReasonInfo(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOW_BATTERY, 0), Call.State.ACTIVE));
    }

    @Test
    @SmallTest
    public void testImsAlternateEmergencyDisconnect() {
        assertEquals(DisconnectCause.IMS_SIP_ALTERNATE_EMERGENCY_CALL,
                mCTUT.getDisconnectCauseFromReasonInfo(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_ALTERNATE_EMERGENCY_CALL, 0),
                        Call.State.ACTIVE));
    }

    @Test
    @SmallTest
    public void testLowBatteryDisconnectDialing() {
        assertEquals(DisconnectCause.DIAL_LOW_BATTERY, mCTUT.getDisconnectCauseFromReasonInfo(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_LOW_BATTERY, 0), Call.State.DIALING));
        assertEquals(DisconnectCause.DIAL_LOW_BATTERY, mCTUT.getDisconnectCauseFromReasonInfo(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOW_BATTERY, 0), Call.State.DIALING));
    }

    /**
     * Tests that no hold tone is played if the call is remotely held and the media direction is
     * send/receive (i.e. there is an audio stream present).
     */
    @Test
    @SmallTest
    public void testNoRemoteHoldtone() {
        //establish a MT call
        testImsMTCallAccept();
        ImsPhoneConnection connection = mCTUT.mForegroundCall.getFirstConnection();
        ImsCall call = connection.getImsCall();

        // Set the media direction to send/receive.
        ImsCallProfile callProfile = new ImsCallProfile();
        callProfile.mMediaProfile.mAudioDirection = ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE;
        call.setCallProfile(callProfile);

        try {
            mCTUT.onCallHoldReceived(call);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        verify(mImsPhone, never()).startOnHoldTone(nullable(Connection.class));
    }

    /**
     * Verifies that a remote hold tone is played when the call is remotely held and the media
     * direction is inactive (i.e. the audio stream is not playing, so we should play the tone).
     */
    @Test
    @SmallTest
    public void testRemoteToneInactive() {
        //establish a MT call
        testImsMTCallAccept();
        ImsPhoneConnection connection = mCTUT.mForegroundCall.getFirstConnection();
        ImsCall call = connection.getImsCall();

        // Set the media direction to inactive to trigger a hold tone.
        ImsCallProfile callProfile = new ImsCallProfile();
        callProfile.mMediaProfile.mAudioDirection = ImsStreamMediaProfile.DIRECTION_INACTIVE;
        call.setCallProfile(callProfile);

        try {
            mCTUT.onCallHoldReceived(call);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        verify(mImsPhone, times(1)).startOnHoldTone(nullable(Connection.class));
    }

    @Test
    @SmallTest
    public void testRemoteHoldtone() {
        // Set carrier config to always play remote hold tone.
        mCTUT.setAlwaysPlayRemoteHoldTone(true);
        //establish a MT call
        testImsMTCallAccept();
        ImsPhoneConnection connection = mCTUT.mForegroundCall.getFirstConnection();
        ImsCall call = connection.getImsCall();

        // Set the media direction to send/receive; normally we don't play a hold tone but the
        // carrier config option is set to ensure we will do it in this case.
        ImsCallProfile callProfile = new ImsCallProfile();
        callProfile.mMediaProfile.mAudioDirection = ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE;
        call.setCallProfile(callProfile);

        try {
            mCTUT.onCallHoldReceived(call);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        verify(mImsPhone, times(1)).startOnHoldTone(nullable(Connection.class));
    }

    @Test
    @SmallTest
    public void testCallRestrictedDisconnect() {
        doReturn(true).when(mSST.mRestrictedState).isCsRestricted();
        assertEquals(DisconnectCause.CS_RESTRICTED, mCTUT.getDisconnectCauseFromReasonInfo(
                new ImsReasonInfo(ImsReasonInfo.CODE_UNSPECIFIED, 0), Call.State.ACTIVE));
    }

    @Test
    @SmallTest
    public void testCallRestrictedEmergencyDisconnect() {
        doReturn(true).when(mSST.mRestrictedState).isCsEmergencyRestricted();
        assertEquals(DisconnectCause.CS_RESTRICTED_EMERGENCY,
                mCTUT.getDisconnectCauseFromReasonInfo(
                        new ImsReasonInfo(ImsReasonInfo.CODE_UNSPECIFIED, 0), Call.State.ACTIVE));
    }

    @Test
    @SmallTest
    public void testCallRestrictedNormal() {
        doReturn(true).when(mSST.mRestrictedState).isCsNormalRestricted();
        assertEquals(DisconnectCause.CS_RESTRICTED_NORMAL,
                mCTUT.getDisconnectCauseFromReasonInfo(
                        new ImsReasonInfo(ImsReasonInfo.CODE_UNSPECIFIED, 0), Call.State.ACTIVE));
    }

    @Test
    @SmallTest
    public void testSipNotFoundRemap() {
        assertEquals(DisconnectCause.INVALID_NUMBER,
                mCTUT.getDisconnectCauseFromReasonInfo(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_NOT_FOUND, 0), Call.State.ACTIVE));
    }

    @Test
    @SmallTest
    public void testCantMakeCallWhileRinging() {
        testImsMTCall();
        try {
            mCTUT.dial("6505551212", VideoProfile.STATE_AUDIO_ONLY, new Bundle());
        } catch (CallStateException e) {
            // We expect a call state exception!
            assertEquals(CallStateException.ERROR_CALL_RINGING, e.getError());
            return;
        }
        Assert.fail("Expected CallStateException");
    }

    @Test
    @SmallTest
    public void testCantMakeCallWhileDialing() {
        startOutgoingCall();
        try {
            mCTUT.dial("6505551212", VideoProfile.STATE_AUDIO_ONLY, new Bundle());
        } catch (CallStateException e) {
            // We expect a call state exception!
            assertEquals(CallStateException.ERROR_ALREADY_DIALING, e.getError());
            return;
        }
        Assert.fail("Expected CallStateException");
    }

    @Test
    @SmallTest
    public void testCantMakeCallTooMany() {
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_ALLOW_HOLD_VIDEO_CALL_BOOL, true);
        mCTUT.updateCarrierConfigCache(bundle);

        // Place a call.
        placeCallAndMakeActive();

        // Place another call
        placeCallAndMakeActive();

        // Finally, dial a third.
        try {
            mCTUT.dial("6505551212", VideoProfile.STATE_AUDIO_ONLY, new Bundle());
        } catch (CallStateException e) {
            // We expect a call state exception!
            assertEquals(CallStateException.ERROR_TOO_MANY_CALLS, e.getError());
            return;
        }
        Assert.fail("Expected CallStateException");
    }

    @Test
    @SmallTest
    public void testMergeComplete() {
        boolean[] result = new boolean[1];
        // Place a call.
        ImsPhoneConnection connection = placeCallAndMakeActive();
        connection.addListener(new Connection.ListenerBase() {
            @Override
            public void onConnectionEvent(String event, Bundle extras) {
                result[0] = android.telecom.Connection.EVENT_MERGE_COMPLETE.equals(event);
            }
        });
        ImsCall call = connection.getImsCall();
        call.getListener().onCallMerged(call, null, false);
        assertTrue(result[0]);
    }

    @Test
    @SmallTest
    public void testNumericOnlyRemap() {
        loadReasonCodeRemap();

        assertEquals(ImsReasonInfo.CODE_SIP_FORBIDDEN, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0)));
        assertEquals(ImsReasonInfo.CODE_SIP_FORBIDDEN, mCTUT.maybeRemapReasonCode(
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0, "")));
    }

    @Test
    @SmallTest
    public void testRemapEmergencyCallsOverWfc() {
        loadReasonCodeRemap();

        assertEquals(ImsReasonInfo.CODE_SIP_SERVICE_UNAVAILABLE,
                mCTUT.maybeRemapReasonCode(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_SERVICE_UNAVAILABLE, 0)));
        assertEquals(ImsReasonInfo.CODE_EMERGENCY_CALL_OVER_WFC_NOT_AVAILABLE,
                mCTUT.maybeRemapReasonCode(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_SERVICE_UNAVAILABLE, 0,
                                "emergency calls over wifi not allowed in this location")));
        assertEquals(ImsReasonInfo.CODE_EMERGENCY_CALL_OVER_WFC_NOT_AVAILABLE,
                mCTUT.maybeRemapReasonCode(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_SERVICE_UNAVAILABLE, 0,
                                "EMERGENCY calls over wifi not allowed in this location")));
    }

    @Test
    @SmallTest
    public void testRemapWfcNotAvailable() {
        loadReasonCodeRemap();

        assertEquals(ImsReasonInfo.CODE_SIP_FORBIDDEN,
                mCTUT.maybeRemapReasonCode(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_FORBIDDEN, 0)));
        assertEquals(ImsReasonInfo.CODE_WFC_SERVICE_NOT_AVAILABLE_IN_THIS_LOCATION,
                mCTUT.maybeRemapReasonCode(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_FORBIDDEN, 0,
                                "Service not allowed in this location")));
        assertEquals(ImsReasonInfo.CODE_WFC_SERVICE_NOT_AVAILABLE_IN_THIS_LOCATION,
                mCTUT.maybeRemapReasonCode(
                        new ImsReasonInfo(ImsReasonInfo.CODE_SIP_FORBIDDEN, 0,
                                "SERVICE not allowed in this location")));
    }

    @Test
    @SmallTest
    public void testNoHoldErrorMessageWhenCallDisconnected() {
        when(mImsPhoneConnection.getImsCall()).thenReturn(mImsCall);
        mCTUT.getConnections().add(mImsPhoneConnection);
        when(mImsPhoneConnection.getState()).thenReturn(ImsPhoneCall.State.DISCONNECTED);
        final ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_UNSPECIFIED,
                ImsReasonInfo.CODE_UNSPECIFIED, null);
        mCTUT.getImsCallListener().onCallHoldFailed(mImsPhoneConnection.getImsCall(), info);
        verify(mImsPhoneConnection, never()).onConnectionEvent(
                eq(android.telecom.Connection.EVENT_CALL_HOLD_FAILED), any());
    }

    @Test
    @SmallTest
    public void testVtDataUsageProvider() throws RemoteException {
        mVtDataUsageProvider.onRequestStatsUpdate(11);

        // Verify that requestStatsUpdate triggers onStatsUpdated, where the initial token should
        // be reported with current stats.
        assertVtDataUsageUpdated(0, 0, 0);

        // Establish a MT call.
        testImsMTCallAccept();
        final ImsPhoneConnection connection = mCTUT.mForegroundCall.getFirstConnection();
        final ImsCall call = connection.getImsCall();
        mCTUT.updateVtDataUsage(call, 51);

        // Make another request, and verify stats updated accordingly, with previously issued token.
        reset(mVtDataUsageProviderCb);
        mVtDataUsageProvider.onRequestStatsUpdate(13);
        assertVtDataUsageUpdated(11, 25, 25);

        // Update accumulated data usage twice. updateVtDataUsage takes accumulated stats from
        // boot up.
        reset(mVtDataUsageProviderCb);
        mCTUT.updateVtDataUsage(call, 70);
        mCTUT.updateVtDataUsage(call, 91);
        verify(mVtDataUsageProviderCb, never()).notifyStatsUpdated(anyInt(), any(), any());

        // Verify that diff stats from last update is reported accordingly.
        mVtDataUsageProvider.onRequestStatsUpdate(13);
        // Rounding error occurs so (70-51)/2 + (91-70)/2 = 19 is expected for both direction.
        assertVtDataUsageUpdated(13, 19, 19);
    }

    @Test
    @SmallTest
    public void testEndRingbackOnSrvcc() throws RemoteException {
        mSecondImsCall.getCallProfile().mMediaProfile = new ImsStreamMediaProfile();
        mSecondImsCall.getCallProfile().mMediaProfile.mAudioDirection =
                ImsStreamMediaProfile.DIRECTION_INACTIVE;

        startOutgoingCall();
        mImsCallListener.onCallProgressing(mSecondImsCall);

        assertTrue(mCTUT.mForegroundCall.isRingbackTonePlaying());

        // Move the connection to the handover state.
        mCTUT.notifySrvccState(Call.SrvccState.COMPLETED);

        assertFalse(mCTUT.mForegroundCall.isRingbackTonePlaying());
    }

    @Test
    @SmallTest
    public void testClearHoldSwapStateOnSrvcc() throws Exception {
        // Answer an incoming call
        testImsMTCall();
        assertTrue(mCTUT.mRingingCall.isRinging());
        try {
            mCTUT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
            verify(mImsCall, times(1)).accept(eq(ImsCallProfile
                    .getCallTypeFromVideoState(ImsCallProfile.CALL_TYPE_VOICE)));
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("set active, unexpected exception thrown" + ex.getMessage());
        }
        assertEquals(Call.State.ACTIVE, mCTUT.mForegroundCall.getState());
        // Hold the call
        doNothing().when(mImsCall).hold();
        try {
            mCTUT.holdActiveCall();
            assertTrue(mCTUT.isHoldOrSwapInProgress());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("hold, unexpected exception thrown" + ex.getMessage());
        }

        // Move the connection to the handover state.
        mCTUT.notifySrvccState(Call.SrvccState.COMPLETED);
        // Ensure we are no longer tracking hold.
        assertFalse(mCTUT.isHoldOrSwapInProgress());
    }

    @Test
    @SmallTest
    public void testHangupHandoverCall() throws RemoteException {
        doReturn("1").when(mImsCallSession).getCallId();
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        // mock a MT call
        mMmTelListener.onIncomingCall(mock(IImsCallSession.class), Bundle.EMPTY);
        verify(mImsPhone, times(1)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(1)).notifyIncomingRing();
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());
        assertTrue(mCTUT.mRingingCall.isRinging());
        assertEquals(1, mCTUT.mRingingCall.getConnections().size());
        ImsPhoneConnection connection =
                (ImsPhoneConnection) mCTUT.mRingingCall.getConnections().get(0);
        connection.addListener(mImsPhoneConnectionListener);

        // Move the connection to the handover state.
        mCTUT.notifySrvccState(Call.SrvccState.COMPLETED);
        assertEquals(1, mCTUT.mHandoverCall.getConnections().size());

        // No need to go through all the rigamarole of the mocked termination we normally do; we
        // can confirm the hangup gets processed without all that.
        doNothing().when(mImsCall).terminate(anyInt());

        try {
            mCTUT.hangup(mCTUT.mHandoverCall);
        } catch (CallStateException e) {
            Assert.fail("CallStateException not expected");
        }
        assertEquals(DisconnectCause.LOCAL, connection.getDisconnectCause());
    }

    /**
     * Verifies that the {@link ImsPhoneCallTracker#getState()} goes to IDLE when an SRVCC takes
     * place.
     * @throws RemoteException
     */
    @Test
    @SmallTest
    public void testTrackerStateOnHandover() throws RemoteException {
        doReturn("1").when(mImsCallSession).getCallId();
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
        assertFalse(mCTUT.mRingingCall.isRinging());
        // mock a MT call
        mMmTelListener.onIncomingCall(mock(IImsCallSession.class), Bundle.EMPTY);
        verify(mImsPhone, times(1)).notifyNewRingingConnection((Connection) any());
        verify(mImsPhone, times(1)).notifyIncomingRing();
        assertEquals(PhoneConstants.State.RINGING, mCTUT.getState());
        assertTrue(mCTUT.mRingingCall.isRinging());
        assertEquals(1, mCTUT.mRingingCall.getConnections().size());
        ImsPhoneConnection connection =
                (ImsPhoneConnection) mCTUT.mRingingCall.getConnections().get(0);
        connection.addListener(mImsPhoneConnectionListener);

        // Move the connection to the handover state.
        mCTUT.notifySrvccState(Call.SrvccState.COMPLETED);
        assertEquals(1, mCTUT.mHandoverCall.getConnections().size());

        // Make sure the tracker states it's idle.
        assertEquals(PhoneConstants.State.IDLE, mCTUT.getState());
    }

    /**
     * Ensures when both RTP and SDP is supported that we register the expected header extension
     * types.
     * @throws Exception
     */
    @Test
    @SmallTest
    public void testConfigureRtpHeaderExtensionTypes() throws Exception {
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_SUPPORTS_DEVICE_TO_DEVICE_COMMUNICATION_USING_RTP_BOOL,
                true);
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_SUPPORTS_SDP_NEGOTIATION_OF_D2D_RTP_HEADER_EXTENSIONS_BOOL,
                true);
        sendCarrierConfigChanged();

        ImsPhoneCallTracker.Config config = new ImsPhoneCallTracker.Config();
        config.isD2DCommunicationSupported = true;
        mCTUT.setConfig(config);
        mConnectorListener.connectionReady(mImsManager, SUB_0);

        // Expect to get offered header extensions since d2d is supported.
        verify(mImsManager).setOfferedRtpHeaderExtensionTypes(
                mRtpHeaderExtensionTypeCaptor.capture());
        Set<RtpHeaderExtensionType> types = mRtpHeaderExtensionTypeCaptor.getValue();
        assertEquals(2, types.size());
        assertTrue(types.contains(RtpTransport.CALL_STATE_RTP_HEADER_EXTENSION_TYPE));
        assertTrue(types.contains(RtpTransport.DEVICE_STATE_RTP_HEADER_EXTENSION_TYPE));
    }

    /**
     * Ensures when SDP is not supported (by RTP is) we don't register any extensions.
     * @throws Exception
     */
    @Test
    @SmallTest
    public void testRtpButNoSdp() throws Exception {
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_SUPPORTS_DEVICE_TO_DEVICE_COMMUNICATION_USING_RTP_BOOL,
                true);
        mContextFixture.getCarrierConfigBundle().putBoolean(
                CarrierConfigManager.KEY_SUPPORTS_SDP_NEGOTIATION_OF_D2D_RTP_HEADER_EXTENSIONS_BOOL,
                false);
        sendCarrierConfigChanged();

        ImsPhoneCallTracker.Config config = new ImsPhoneCallTracker.Config();
        config.isD2DCommunicationSupported = true;
        mCTUT.setConfig(config);
        mConnectorListener.connectionReady(mImsManager, SUB_0);

        // Expect to get offered header extensions since d2d is supported.
        verify(mImsManager).setOfferedRtpHeaderExtensionTypes(
                mRtpHeaderExtensionTypeCaptor.capture());
        Set<RtpHeaderExtensionType> types = mRtpHeaderExtensionTypeCaptor.getValue();
        assertEquals(0, types.size());
    }

    /**
     * Ensures when D2D communication is not supported that we don't register the D2D RTP header
     * extension types.
     * @throws Exception
     */
    @Test
    @SmallTest
    public void testDontConfigureRtpHeaderExtensionTypes() throws Exception {
        mConnectorListener.connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        sendCarrierConfigChanged();
        ImsPhoneCallTracker.Config config = new ImsPhoneCallTracker.Config();
        config.isD2DCommunicationSupported = false;
        mCTUT.setConfig(config);
        mConnectorListener.connectionReady(mImsManager, SUB_0);

        // Expect no offered header extensions since d2d is not supported.
        verify(mImsManager, never()).setOfferedRtpHeaderExtensionTypes(any());
    }

    @Test
    @SmallTest
    public void testCleanupAndRemoveConnection() throws Exception {
        ImsPhoneConnection conn = placeCall();
        assertEquals(1, mCTUT.getConnections().size());
        assertNotNull(mCTUT.getPendingMO());
        assertEquals(Call.State.DIALING, mCTUT.mForegroundCall.getState());

        mCTUT.cleanupAndRemoveConnection(conn);
        assertEquals(0, mCTUT.getConnections().size());
        assertNull(mCTUT.getPendingMO());
        assertEquals(Call.State.IDLE, mCTUT.mForegroundCall.getState());
    }

    @Test
    @SmallTest
    public void testCallSessionUpdatedAfterSrvccCompleted() throws RemoteException {
        startOutgoingCall();

        // Move the connection to the handover state.
        mCTUT.notifySrvccState(Call.SrvccState.COMPLETED);

        try {
            // When trigger CallSessionUpdated after Srvcc completes, checking no exception.
            mImsCallListener.onCallUpdated(mSecondImsCall);
        } catch (Exception ex) {
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
    }

    /**
     * Tests the case where a dialed call has not yet moved beyond the "pending MO" phase, but the
     * user then disconnects.  In such a case we need to ensure that the pending MO reference is
     * cleared so that another call can be placed.
     */
    @Test
    @SmallTest
    public void testCallDisconnectBeforeActive() {
        ImsPhoneConnection connection = placeCall();
        assertEquals(1, mCTUT.getConnections().size());
        // Call is the pending MO right now.
        assertEquals(connection, mCTUT.getPendingMO());
        assertEquals(Call.State.DIALING, mCTUT.mForegroundCall.getState());
        assertEquals(PhoneConstants.State.OFFHOOK, mCTUT.getState());

        mImsCallListener.onCallTerminated(connection.getImsCall(),
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));
        // Make sure pending MO got nulled out.
        assertNull(mCTUT.getPendingMO());

        // Try making another call; it should not fail.
        ImsPhoneConnection connection2 = placeCall();
    }

    private void sendCarrierConfigChanged() {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, mPhone.getSubId());
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, mPhone.getPhoneId());
        mBroadcastReceiver.onReceive(mContext, intent);
        processAllMessages();
    }

    private void assertVtDataUsageUpdated(int expectedToken, long rxBytes, long txBytes)
            throws RemoteException {
        final ArgumentCaptor<NetworkStats> ifaceStatsCaptor = ArgumentCaptor.forClass(
                NetworkStats.class);
        final ArgumentCaptor<NetworkStats> uidStatsCaptor = ArgumentCaptor.forClass(
                NetworkStats.class);

        verify(mVtDataUsageProviderCb).notifyStatsUpdated(eq(expectedToken),
                ifaceStatsCaptor.capture(), uidStatsCaptor.capture());

        // Default dialer's package uid is not set during test, thus the uid stats looks the same
        // as iface stats and the records are always merged into the same entry.
        // TODO: Mock different dialer's uid and verify uid stats has corresponding uid in the
        //  records.
        NetworkStats expectedStats = new NetworkStats(0L, 0);

        if (rxBytes != 0 || txBytes != 0) {
            expectedStats = expectedStats.addEntry(
                    new Entry(mCTUT.getVtInterface(), UID_ALL, SET_FOREGROUND,
                            TAG_NONE, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES, rxBytes, 0L,
                            txBytes, 0L, 0L));
        }
        assertNetworkStatsEquals(expectedStats, ifaceStatsCaptor.getValue());
        assertNetworkStatsEquals(expectedStats, uidStatsCaptor.getValue());
    }

    private ImsPhoneConnection placeCallAndMakeActive() {
        ImsPhoneConnection connection = placeCall();
        ImsCall imsCall = connection.getImsCall();
        imsCall.getImsCallSessionListenerProxy().callSessionProgressing(imsCall.getSession(),
                new ImsStreamMediaProfile());
        imsCall.getImsCallSessionListenerProxy().callSessionStarted(imsCall.getSession(),
                new ImsCallProfile());
        return connection;
    }

    private ImsPhoneConnection placeCall() {
        try {
            doAnswer(new Answer<ImsCall>() {
                @Override
                public ImsCall answer(InvocationOnMock invocation) throws Throwable {
                    mImsCallListener =
                            (ImsCall.Listener) invocation.getArguments()[2];
                    ImsCall imsCall = spy(new ImsCall(mContext, mImsCallProfile));
                    imsCall.setListener(mImsCallListener);
                    imsCallMocking(imsCall);
                    return imsCall;
                }

            }).when(mImsManager).makeCall(eq(mImsCallProfile), (String[]) any(),
                    (ImsCall.Listener) any());
        } catch (ImsException ie) {
        }

        ImsPhoneConnection connection = null;
        try {
            connection = (ImsPhoneConnection) mCTUT.dial("+16505551212",
                    ImsCallProfile.CALL_TYPE_VOICE, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("unexpected exception thrown" + ex.getMessage());
        }
        if (connection == null) {
            Assert.fail("connection is null");
        }
        return connection;
    }
}

