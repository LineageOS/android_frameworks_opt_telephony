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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Unit tests for {@link com.android.internal.telephony.CarrierServiceStateTracker}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierServiceStateTrackerTest extends TelephonyTest {
    public static final String LOG_TAG = "CSST";

    private CarrierServiceStateTracker mSpyCarrierSST;
    private CarrierServiceStateTracker mCarrierSST;
    private CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;

    private static final int SUB_ID = 1;

    NotificationManager mNotificationManager;
    PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        logd(LOG_TAG + "Setup!");
        super.setUp(getClass().getSimpleName());
        doReturn((Executor) Runnable::run).when(mContext).getMainExecutor();
        mBundle = mContextFixture.getCarrierConfigBundle();
        when(mPhone.getSubId()).thenReturn(SUB_ID);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(mBundle);

        // Capture listener to emulate the carrier config change notification used later
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        mCarrierSST = new CarrierServiceStateTracker(mPhone, mSST);
        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                listenerArgumentCaptor.capture());
        mCarrierConfigChangeListener = listenerArgumentCaptor.getAllValues().get(0);
        mSpyCarrierSST = spy(mCarrierSST);

        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);

        setCarrierPrivilegesForSubId(true, SUB_ID);

        setDefaultValues();
        processAllMessages();
    }

    private void setDefaultValues() {
        mBundle.putInt(CarrierConfigManager.KEY_PREF_NETWORK_NOTIFICATION_DELAY_INT, 0);
        mBundle.putInt(CarrierConfigManager.KEY_EMERGENCY_NOTIFICATION_DELAY_INT, 0);
        mBundle.putBoolean(CarrierConfigManager.KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL, false);
    }

    @After
    public void tearDown() throws Exception {
        mCarrierSST = null;
        mSpyCarrierSST = null;
        mBundle = null;
        mNotificationManager = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testCancelBothNotifications() {
        logd(LOG_TAG + ":testCancelBothNotifications()");
        Message notificationMsg = mSpyCarrierSST.obtainMessage(
                CarrierServiceStateTracker.CARRIER_EVENT_DATA_REGISTRATION, null);
        doReturn(false).when(mSpyCarrierSST).evaluateSendingMessage(any());
        doReturn(mNotificationManager).when(mSpyCarrierSST).getNotificationManager(any());
        mSpyCarrierSST.handleMessage(notificationMsg);
        processAllMessages();
        verify(mNotificationManager).cancel(
                CarrierServiceStateTracker.EMERGENCY_NOTIFICATION_TAG, SUB_ID);
        verify(mNotificationManager).cancel(
                CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG, SUB_ID);

    }

    @Test
    @SmallTest
    public void testSendBothNotifications() {
        logd(LOG_TAG + ":testSendBothNotifications()");
        Notification.Builder mNotificationBuilder = new Notification.Builder(mContext);
        Message notificationMsg = mSpyCarrierSST.obtainMessage(
                CarrierServiceStateTracker.CARRIER_EVENT_DATA_DEREGISTRATION, null);
        doReturn(true).when(mSpyCarrierSST).evaluateSendingMessage(any());
        doReturn(false).when(mSpyCarrierSST).isRadioOffOrAirplaneMode();
        doReturn(0).when(mSpyCarrierSST).getDelay(any());
        doReturn(mNotificationBuilder).when(mSpyCarrierSST).getNotificationBuilder(any());
        doReturn(mNotificationManager).when(mSpyCarrierSST).getNotificationManager(any());
        mSpyCarrierSST.handleMessage(notificationMsg);
        processAllMessages();
        verify(mNotificationManager).notify(
                eq(CarrierServiceStateTracker.EMERGENCY_NOTIFICATION_TAG),
                eq(SUB_ID), isA(Notification.class));
        verify(mNotificationManager).notify(
                eq(CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG),
                eq(SUB_ID), any());
    }

    @Test
    @SmallTest
    public void testSendPrefNetworkNotification() {
        logd(LOG_TAG + ":testSendPrefNetworkNotification()");
        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */, SUB_ID,
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();

        Map<Integer, CarrierServiceStateTracker.NotificationType> notificationTypeMap =
                mCarrierSST.getNotificationTypeMap();
        CarrierServiceStateTracker.NotificationType prefNetworkNotification =
                notificationTypeMap.get(CarrierServiceStateTracker.NOTIFICATION_PREF_NETWORK);
        CarrierServiceStateTracker.NotificationType spyPrefNetworkNotification = spy(
                prefNetworkNotification);
        notificationTypeMap.put(CarrierServiceStateTracker.NOTIFICATION_PREF_NETWORK,
                spyPrefNetworkNotification);
        Notification.Builder mNotificationBuilder = new Notification.Builder(mContext);
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST.mSS).getState();
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST.mSS).getDataRegistrationState();
        doReturn(true).when(mSST).isRadioOn();
        doReturn(mNotificationBuilder).when(spyPrefNetworkNotification).getNotificationBuilder();


        long networkType = (long) RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO);
        int allowedNetworkTypeReason = TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER;
        long allowedNetworkTypeValue = networkType;
        doReturn(networkType).when(mPhone).getAllowedNetworkTypes(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        mSpyCarrierSST.getAllowedNetworkTypesChangedListener().onAllowedNetworkTypesChanged(
                allowedNetworkTypeReason, allowedNetworkTypeValue);

        processAllMessages();
        verify(mNotificationManager, atLeast(1)).notify(
                eq(CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG),
                eq(SUB_ID), isA(Notification.class));

        networkType = (long) RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
        allowedNetworkTypeValue = networkType;
        doReturn(networkType).when(mPhone).getAllowedNetworkTypes(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        mSpyCarrierSST.getAllowedNetworkTypesChangedListener().onAllowedNetworkTypesChanged(
                allowedNetworkTypeReason, allowedNetworkTypeValue);

        processAllMessages();
        verify(mNotificationManager, atLeast(1)).cancel(
                CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG, SUB_ID);
    }

    @Test
    @SmallTest
    public void testSendEmergencyNetworkNotification() {
        logd(LOG_TAG + ":testSendEmergencyNetworkNotification()");
        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */, SUB_ID,
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();

        Map<Integer, CarrierServiceStateTracker.NotificationType> notificationTypeMap =
                mCarrierSST.getNotificationTypeMap();
        CarrierServiceStateTracker.NotificationType emergencyNetworkNotification =
                notificationTypeMap.get(CarrierServiceStateTracker.NOTIFICATION_EMERGENCY_NETWORK);
        CarrierServiceStateTracker.NotificationType spyEmergencyNetworkNotification = spy(
                emergencyNetworkNotification);
        notificationTypeMap.put(CarrierServiceStateTracker.NOTIFICATION_EMERGENCY_NETWORK,
                spyEmergencyNetworkNotification);
        Notification.Builder mNotificationBuilder = new Notification.Builder(mContext);
        doReturn(mNotificationBuilder).when(spyEmergencyNetworkNotification)
                .getNotificationBuilder();

        doReturn(true).when(mPhone).isWifiCallingEnabled();
        Message notificationMsg = mSpyCarrierSST.obtainMessage(
                CarrierServiceStateTracker.CARRIER_EVENT_IMS_CAPABILITIES_CHANGED, null);
        mSpyCarrierSST.handleMessage(notificationMsg);
        processAllMessages();
        verify(mNotificationManager).notify(
                eq(CarrierServiceStateTracker.EMERGENCY_NOTIFICATION_TAG),
                eq(SUB_ID), isA(Notification.class));

        doReturn(false).when(mPhone).isWifiCallingEnabled();
        mSpyCarrierSST.handleMessage(notificationMsg);
        processAllMessages();
        verify(mNotificationManager, atLeast(2)).cancel(
                CarrierServiceStateTracker.EMERGENCY_NOTIFICATION_TAG, SUB_ID);
    }

    @Test
    public void testSetEnabledNotifications() {
        logd(LOG_TAG + ":testSetEnabledNotifications()");

        mBundle.putBoolean(CarrierConfigManager.KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL, true);

        Notification.Builder mNotificationBuilder = new Notification.Builder(mContext);
        doReturn(mNotificationBuilder).when(mSpyCarrierSST).getNotificationBuilder(any());
        doReturn(mNotificationManager).when(mSpyCarrierSST).getNotificationManager(any());
        doReturn(true).when(mPhone).isWifiCallingEnabled(); // notifiable for emergency
        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */, SUB_ID,
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();

        Map<Integer, CarrierServiceStateTracker.NotificationType> notificationTypeMap =
                mCarrierSST.getNotificationTypeMap();
        CarrierServiceStateTracker.NotificationType prefNetworkNotification =
                notificationTypeMap.get(CarrierServiceStateTracker.NOTIFICATION_PREF_NETWORK);
        CarrierServiceStateTracker.NotificationType emergencyNetworkNotification =
                notificationTypeMap.get(CarrierServiceStateTracker.NOTIFICATION_EMERGENCY_NETWORK);
        assertFalse(prefNetworkNotification.isEnabled());
        assertTrue(emergencyNetworkNotification.isEnabled());

        verify(mNotificationManager, never()).notify(
                eq(CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG),
                eq(SUB_ID), isA(Notification.class));
        verify(mNotificationManager, atLeast(1)).cancel(
                CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG, SUB_ID);
        verify(mNotificationManager, atLeast(1)).notify(
                eq(CarrierServiceStateTracker.EMERGENCY_NOTIFICATION_TAG),
                eq(SUB_ID), isA(Notification.class));
        verify(mNotificationManager, never()).cancel(
                CarrierServiceStateTracker.EMERGENCY_NOTIFICATION_TAG, SUB_ID);
    }
}
