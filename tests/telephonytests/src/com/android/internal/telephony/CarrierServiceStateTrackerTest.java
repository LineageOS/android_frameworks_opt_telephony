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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.Map;

/**
 * Unit tests for {@link com.android.internal.telephony.CarrierServiceStateTracker}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierServiceStateTrackerTest extends TelephonyTest {
    public static final String LOG_TAG = "CSST";

    private CarrierServiceStateTracker mSpyCarrierSST;
    private CarrierServiceStateTracker mCarrierSST;

    private static final int SUB_ID = 1;

    NotificationManager mNotificationManager;
    PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        logd(LOG_TAG + "Setup!");
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        when(mPhone.getSubId()).thenReturn(SUB_ID);
        mCarrierSST = new CarrierServiceStateTracker(mPhone, mSST);
        mSpyCarrierSST = spy(mCarrierSST);

        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);

        setDefaultValues();
        processAllMessages();
    }

    private void setDefaultValues() {
        mBundle.putInt(CarrierConfigManager.KEY_PREF_NETWORK_NOTIFICATION_DELAY_INT, 0);
        mBundle.putInt(CarrierConfigManager.KEY_EMERGENCY_NOTIFICATION_DELAY_INT, 0);
    }

    @After
    public void tearDown() throws Exception {
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
        Intent intent = new Intent().setAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.sendBroadcast(intent);
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

        String prefNetworkMode = Settings.Global.PREFERRED_NETWORK_MODE + mPhone.getSubId();
        Settings.Global.putInt(mContext.getContentResolver(), prefNetworkMode,
                RILConstants.NETWORK_MODE_LTE_CDMA_EVDO);
        mSpyCarrierSST.getContentObserver().dispatchChange(false,
                Settings.Global.getUriFor(prefNetworkMode));
        processAllMessages();
        verify(mNotificationManager, atLeast(1)).notify(
                eq(CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG),
                eq(SUB_ID), isA(Notification.class));


        Settings.Global.putInt(mContext.getContentResolver(), prefNetworkMode,
                RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
        mSpyCarrierSST.getContentObserver().dispatchChange(false,
                Settings.Global.getUriFor(prefNetworkMode));
        processAllMessages();
        verify(mNotificationManager, atLeast(1)).cancel(
                CarrierServiceStateTracker.PREF_NETWORK_NOTIFICATION_TAG, SUB_ID);
    }

    @Test
    @SmallTest
    public void testSendEmergencyNetworkNotification() {
        logd(LOG_TAG + ":testSendEmergencyNetworkNotification()");
        Intent intent = new Intent().setAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.sendBroadcast(intent);
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
}
