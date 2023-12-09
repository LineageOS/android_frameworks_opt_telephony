/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telecom.Call;
import android.telecom.Connection;
import android.telephony.BinderCacheManager;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Unit tests for SatelliteSOSMessageRecommender
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SatelliteSOSMessageRecommenderTest extends TelephonyTest {
    private static final String TAG = "SatelliteSOSMessageRecommenderTest";
    private static final long TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS = 500;
    private static final int PHONE_ID = 0;
    private static final String CALL_ID = "CALL_ID";
    private static final String WRONG_CALL_ID = "WRONG_CALL_ID";
    private TestSatelliteController mTestSatelliteController;
    private TestImsManager mTestImsManager;

    @Mock
    private Context mMockContext;
    @Mock
    private Resources mResources;
    @Mock
    private ImsManager.MmTelFeatureConnectionFactory mMmTelFeatureConnectionFactory;
    private TestConnection mTestConnection;
    private TestSOSMessageRecommender mTestSOSMessageRecommender;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getMainLooper()).thenReturn(Looper.myLooper());
        when(mMockContext.getResources()).thenReturn(mResources);
        when(mResources.getString(com.android.internal.R.string.config_satellite_service_package))
                .thenReturn("");
        when(mMockContext.getSystemServiceName(CarrierConfigManager.class))
                .thenReturn("CarrierConfigManager");
        when(mMockContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mCarrierConfigManager);
        when(mMockContext.getSystemServiceName(SubscriptionManager.class))
                .thenReturn("SubscriptionManager");
        when(mMockContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mSubscriptionManager);
        mTestSatelliteController = new TestSatelliteController(mMockContext,
                Looper.myLooper());
        mTestImsManager = new TestImsManager(
                mMockContext, PHONE_ID, mMmTelFeatureConnectionFactory, null, null, null);
        mTestConnection = new TestConnection(CALL_ID);
        when(mPhone.getServiceState()).thenReturn(mServiceState);
        mTestSOSMessageRecommender = new TestSOSMessageRecommender(Looper.myLooper(),
                mTestSatelliteController, mTestImsManager,
                TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mPhone.isImsRegistered()).thenReturn(false);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
        assertTrue(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
    }

    @Test
    public void testStopTrackingCallBeforeTimeout_ConnectionActive() {
        testStopTrackingCallBeforeTimeout(Connection.STATE_ACTIVE);
    }

    @Test
    public void testStopTrackingCallBeforeTimeout_ConnectionDisconnected() {
        testStopTrackingCallBeforeTimeout(Connection.STATE_DISCONNECTED);
    }

    @Test
    public void testImsRegistrationStateChangedBeforeTimeout() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        mTestImsManager.sendImsRegistrationStateChangedEvent(true);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 0, 0, 0);

        mTestImsManager.sendImsRegistrationStateChangedEvent(false);
        processAllMessages();
        assertEquals(2, mTestSOSMessageRecommender.getCountOfTimerStarted());

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
    }

    @Test
    public void testSatelliteProvisionStateChangedBeforeTimeout() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        mTestSatelliteController.sendProvisionStateChangedEvent(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false);
        processAllMessages();

        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 2, 2, 2);

        mTestSatelliteController.sendProvisionStateChangedEvent(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true);

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 2, 2, 2);
    }

    @Test
    public void testEmergencyCallRedialBeforeTimeout() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        Phone newPhone = Mockito.mock(Phone.class);
        when(newPhone.getServiceState()).thenReturn(mServiceState);
        when(newPhone.isImsRegistered()).thenReturn(false);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, newPhone);
        processAllMessages();

        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        /**
         * Since {@link SatelliteSOSMessageRecommender} always uses
         * {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} when registering for provision state
         * changed events with {@link SatelliteController}, registerForProvisionCount does
         * not depend on Phone.
         * <p>
         * Since we use a single mocked ImsManager instance, registerForImsCount does not depend on
         * Phone.
         */
        assertRegisterForStateChangedEventsTriggered(newPhone, 2, 2, 1);

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        /**
         * Since {@link SatelliteSOSMessageRecommender} always uses
         * {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} when unregistering for provision
         * state changed events with {@link SatelliteController}, unregisterForProvisionCount does
         * not depend on Phone.
         * <p>
         * Since we use a single mocked ImsManager instance, unregisterForImsCount does not depend
         * on Phone.
         */
        assertUnregisterForStateChangedEventsTriggered(newPhone, 2, 2, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_InServiceToOutOfService() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_IN_SERVICE, ServiceState.STATE_OUT_OF_SERVICE);
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_InServiceToPowerOff() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_IN_SERVICE, ServiceState.STATE_POWER_OFF);
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_EmergencyOnlyToOutOfService() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_EMERGENCY_ONLY, ServiceState.STATE_OUT_OF_SERVICE);
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_EmergencyOnlyToPowerOff() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_EMERGENCY_ONLY, ServiceState.STATE_POWER_OFF);
    }

    @Test
    public void testOnEmergencyCallConnectionStateChangedWithWrongCallId() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                WRONG_CALL_ID, Connection.STATE_ACTIVE);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
    }

    @Test
    public void testSatelliteNotAllowedInCurrentLocation() {
        mTestSatelliteController.setIsSatelliteCommunicationAllowed(false);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        /**
         * We should have registered for the state change events abd started the timer when
         * receiving the event onEmergencyCallStarted. After getting the callback for the result of
         * the request requestIsSatelliteCommunicationAllowedForCurrentLocation, the resources
         * should be cleaned up.
         */
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
    }

    @Test
    public void testOnEmergencyCallStarted() {
        SatelliteController satelliteController = new SatelliteController(
                mMockContext, Looper.myLooper());
        TestSOSMessageRecommender testSOSMessageRecommender = new TestSOSMessageRecommender(
                Looper.myLooper(),
                satelliteController, mTestImsManager,
                TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        testSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertFalse(testSOSMessageRecommender.isTimerStarted());
        assertEquals(0, testSOSMessageRecommender.getCountOfTimerStarted());
    }

    private void testStopTrackingCallBeforeTimeout(
            @Connection.ConnectionState int connectionState) {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(CALL_ID, connectionState);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
    }

    private void testCellularServiceStateChangedBeforeTimeout(
            @ServiceState.RegState int availableServiceState,
            @ServiceState.RegState int unavailableServiceState) {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, mPhone);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);

        mTestSOSMessageRecommender.sendServiceStateChangedEvent(availableServiceState);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 0, 0, 0);

        mTestSOSMessageRecommender.sendServiceStateChangedEvent(unavailableServiceState);
        processAllMessages();
        assertEquals(2, mTestSOSMessageRecommender.getCountOfTimerStarted());

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(Call.EVENT_DISPLAY_SOS_MESSAGE));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
    }

    private void assertRegisterForStateChangedEventsTriggered(
            Phone phone, int registerForProvisionCount, int registerForImsCount,
            int registerForCellularCount) {
        assertEquals(registerForProvisionCount,
                mTestSatelliteController.getRegisterForSatelliteProvisionStateChangedCalls());
        assertEquals(registerForImsCount, mTestImsManager.getAddRegistrationCallbackCalls());
        verify(phone, times(registerForCellularCount))
                .registerForServiceStateChanged(any(), anyInt(), any());
    }

    private void assertUnregisterForStateChangedEventsTriggered(
            Phone phone, int unregisterForProvisionCount, int unregisterForImsCount,
            int unregisterForCellularCount) {
        assertEquals(unregisterForProvisionCount,
                mTestSatelliteController.getUnregisterForSatelliteProvisionStateChangedCalls());
        assertEquals(unregisterForImsCount, mTestImsManager.getRemoveRegistrationListenerCalls());
        verify(phone, times(unregisterForCellularCount)).unregisterForServiceStateChanged(any());
    }

    private static class TestSatelliteController extends SatelliteController {

        private static final String TAG = "TestSatelliteController";
        private final Map<Integer, Set<ISatelliteProvisionStateCallback>>
                mProvisionStateChangedCallbacks;
        private int mRegisterForSatelliteProvisionStateChangedCalls = 0;
        private int mUnregisterForSatelliteProvisionStateChangedCalls = 0;
        private boolean mIsSatelliteProvisioned = true;
        private boolean mIsSatelliteCommunicationAllowed = true;

        /**
         * Create a SatelliteController to act as a backend service of
         * {@link SatelliteManager}
         *
         * @param context The Context for the SatelliteController.
         */
        protected TestSatelliteController(Context context, Looper looper) {
            super(context, looper);
            mProvisionStateChangedCallbacks = new HashMap<>();
        }

        @Override
        public Boolean isSatelliteProvisioned() {
            return mIsSatelliteProvisioned;
        }

        @Override
        public boolean isSatelliteSupported() {
            return true;
        }

        @Override
        @SatelliteManager.SatelliteError public int registerForSatelliteProvisionStateChanged(
                int subId, @NonNull ISatelliteProvisionStateCallback callback) {
            mRegisterForSatelliteProvisionStateChangedCalls++;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.getOrDefault(subId, new HashSet<>());
            perSubscriptionCallbacks.add(callback);
            mProvisionStateChangedCallbacks.put(subId, perSubscriptionCallbacks);
            return SatelliteManager.SATELLITE_ERROR_NONE;
        }

        @Override
        public void unregisterForSatelliteProvisionStateChanged(
                int subId, @NonNull ISatelliteProvisionStateCallback callback) {
            mUnregisterForSatelliteProvisionStateChangedCalls++;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.get(subId);
            if (perSubscriptionCallbacks != null) {
                perSubscriptionCallbacks.remove(callback);
            }
        }

        @Override
        public void requestIsSatelliteCommunicationAllowedForCurrentLocation(int subId,
                @NonNull ResultReceiver result) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED,
                    mIsSatelliteCommunicationAllowed);
            result.send(SatelliteManager.SATELLITE_ERROR_NONE, bundle);
        }

        public void setIsSatelliteCommunicationAllowed(boolean allowed) {
            mIsSatelliteCommunicationAllowed = allowed;
        }

        public int getRegisterForSatelliteProvisionStateChangedCalls() {
            return mRegisterForSatelliteProvisionStateChangedCalls;
        }

        public int getUnregisterForSatelliteProvisionStateChangedCalls() {
            return mUnregisterForSatelliteProvisionStateChangedCalls;
        }

        public void sendProvisionStateChangedEvent(int subId, boolean provisioned) {
            mIsSatelliteProvisioned = provisioned;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.get(subId);
            if (perSubscriptionCallbacks != null) {
                for (ISatelliteProvisionStateCallback callback : perSubscriptionCallbacks) {
                    try {
                        callback.onSatelliteProvisionStateChanged(provisioned);
                    } catch (RemoteException ex) {
                        Log.e(TAG, "sendProvisionStateChangedEvent: ex=" + ex);
                    }
                }
            }
        }
    }

    private static class TestImsManager extends ImsManager {

        private final Set<RegistrationManager.RegistrationCallback> mCallbacks;
        private int mAddRegistrationCallbackCalls = 0;
        private int mRemoveRegistrationListenerCalls = 0;

        /**
         * Used for testing only to inject dependencies.
         */
        TestImsManager(Context context, int phoneId, MmTelFeatureConnectionFactory factory,
                SubscriptionManagerProxy subManagerProxy, SettingsProxy settingsProxy,
                BinderCacheManager binderCacheManager) {
            super(context, phoneId, factory, subManagerProxy, settingsProxy, binderCacheManager);
            mCallbacks = new HashSet<>();
        }

        @Override
        public void addRegistrationCallback(RegistrationManager.RegistrationCallback callback,
                Executor executor)
                throws ImsException {
            mAddRegistrationCallbackCalls++;

            if (callback == null) {
                throw new NullPointerException("registration callback can't be null");
            }
            if (executor == null) {
                throw new NullPointerException("registration executor can't be null");
            }

            callback.setExecutor(executor);
            mCallbacks.add(callback);
        }

        @Override
        public void removeRegistrationListener(RegistrationManager.RegistrationCallback callback) {
            mRemoveRegistrationListenerCalls++;

            if (callback == null) {
                throw new NullPointerException("registration callback can't be null");
            }
            mCallbacks.remove(callback);
        }

        public int getAddRegistrationCallbackCalls() {
            return mAddRegistrationCallbackCalls;
        }

        public int getRemoveRegistrationListenerCalls() {
            return mRemoveRegistrationListenerCalls;
        }

        public void sendImsRegistrationStateChangedEvent(boolean registered) {
            if (registered) {
                for (RegistrationManager.RegistrationCallback callback : mCallbacks) {
                    callback.onRegistered(null);
                }
            } else {
                for (RegistrationManager.RegistrationCallback callback : mCallbacks) {
                    callback.onUnregistered(null);
                }
            }
        }
    }

    private static class TestSOSMessageRecommender extends SatelliteSOSMessageRecommender {

        /**
         * Create an instance of SatelliteSOSMessageRecommender.
         *
         * @param looper              The looper used with the handler of this class.
         * @param satelliteController The SatelliteController singleton instance.
         * @param imsManager          The ImsManager instance associated with the phone, which is
         *                            used for making the emergency call. This argument is not
         *                            null only in unit tests.
         * @param timeoutMillis       The timeout duration of the timer.
         */
        TestSOSMessageRecommender(Looper looper, SatelliteController satelliteController,
                ImsManager imsManager, long timeoutMillis) {
            super(looper, satelliteController, imsManager, timeoutMillis);
        }

        public boolean isTimerStarted() {
            return hasMessages(EVENT_TIME_OUT);
        }

        public int getCountOfTimerStarted() {
            return mCountOfTimerStarted;
        }

        public void sendServiceStateChangedEvent(@ServiceState.RegState int state) {
            ServiceState serviceState = new ServiceState();
            serviceState.setState(state);
            sendMessage(obtainMessage(EVENT_CELLULAR_SERVICE_STATE_CHANGED,
                    new AsyncResult(null, serviceState, null)));
        }
    }

    private static class TestConnection extends Connection {
        private final Set<String> mSentEvents;
        TestConnection(String callId) {
            setTelecomCallId(callId);
            mSentEvents = new HashSet<>();
        }

        @Override
        public void sendConnectionEvent(String event, Bundle extras) {
            mSentEvents.add(event);
        }

        public boolean isEventSent(String event) {
            return mSentEvents.contains(event);
        }
    }
}
