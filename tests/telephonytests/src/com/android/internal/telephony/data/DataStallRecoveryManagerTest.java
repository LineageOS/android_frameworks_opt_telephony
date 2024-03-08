/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.database.ContentObserver;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import com.android.internal.telephony.data.DataStallRecoveryManager.DataStallRecoveryManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataStallRecoveryManagerTest extends TelephonyTest {
    private FakeContentResolver mFakeContentResolver;

    // Mocked classes
    private DataStallRecoveryManagerCallback mDataStallRecoveryManagerCallback;

    private DataStallRecoveryManager mDataStallRecoveryManager;

    /**
     * The fake content resolver used to receive change event from global settings
     * and notify observer of a change in content in DataStallRecoveryManager
     */
    private class FakeContentResolver extends MockContentResolver {
        @Override
        public void notifyChange(Uri uri, ContentObserver observer) {
            super.notifyChange(uri, observer);
            logd("onChanged(uri=" + uri + ")" + observer);
            if (observer != null) {
                observer.dispatchChange(false, uri);
            } else {
                mDataStallRecoveryManager.getContentObserver().dispatchChange(false, uri);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DataStallRecoveryManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        Field field = DataStallRecoveryManager.class.getDeclaredField("mPredictWaitingMillis");
        field.setAccessible(true);

        mFakeContentResolver = new FakeContentResolver();
        doReturn(mFakeContentResolver).when(mContext).getContentResolver();
        // Set the global settings for action enabled state and duration to
        // the default test values.
        Settings.Global.putString(mFakeContentResolver, Settings.Global.DSRM_DURATION_MILLIS,
                "100,100,100,100,0");
        Settings.Global.putString(mFakeContentResolver, Settings.Global.DSRM_ENABLED_ACTIONS,
                "true,true,false,true,true");

        mDataStallRecoveryManagerCallback = mock(DataStallRecoveryManagerCallback.class);
        mCarrierConfigManager = mPhone.getContext().getSystemService(CarrierConfigManager.class);
        long[] dataStallRecoveryTimersArray = new long[] {100, 100, 100, 100};
        boolean[] dataStallRecoveryStepsArray = new boolean[] {false, false, true, false, false};
        doReturn(dataStallRecoveryTimersArray)
                .when(mDataConfigManager)
                .getDataStallRecoveryDelayMillis();
        doReturn(dataStallRecoveryStepsArray)
                .when(mDataConfigManager)
                .getDataStallRecoveryShouldSkipArray();
        doReturn(true).when(mDataNetworkController).isInternetDataAllowed();

        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mDataStallRecoveryManagerCallback).invokeFromExecutor(any(Runnable.class));

        mDataStallRecoveryManager =
                new DataStallRecoveryManager(
                        mPhone,
                        mDataNetworkController,
                        mMockedWwanDataServiceManager,
                        mTestableLooper.getLooper(),
                        mDataStallRecoveryManagerCallback);

        field.set(mDataStallRecoveryManager, 0L);

        logd("DataStallRecoveryManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mFakeContentResolver = null;
        mDataStallRecoveryManager = null;
        super.tearDown();
    }

    private void sendValidationStatusCallback(@ValidationStatus int status) {
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController, times(2))
                .registerDataNetworkControllerCallback(
                        dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback dataNetworkControllerCallback =
                dataNetworkControllerCallbackCaptor.getAllValues().get(0);
        dataNetworkControllerCallback.onInternetDataNetworkValidationStatusChanged(status);
    }

    private void sendOnInternetDataNetworkCallback(boolean isConnected) {
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController, times(2))
                .registerDataNetworkControllerCallback(
                        dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback dataNetworkControllerCallback =
                dataNetworkControllerCallbackCaptor.getAllValues().get(0);

        DataNetwork network = mock(DataNetwork.class);
        NetworkCapabilities netCaps = new NetworkCapabilities();
        doReturn(netCaps).when(network).getNetworkCapabilities();
        if (!isConnected) {
            // A network that doesn't need to be tracked for validation
            netCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }
        dataNetworkControllerCallback.onConnectedInternetDataNetworksChanged(Set.of(network));
        processAllMessages();
    }

    @Test
    public void testRecoveryStepPDPReset() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(1);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        verify(mDataStallRecoveryManagerCallback).onDataStallReestablishInternet();
    }

    @Test
    public void testRecoveryStepRestartRadio() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(3);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        verify(mSST, times(1)).powerOffRadioSafely();
    }

    @Test
    public void testRecoveryStepModemReset() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(4);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);

        processAllFutureMessages();

        verify(mPhone, times(1)).rebootModem(any());
    }

    @Test
    public void testDoNotDoRecoveryActionWhenPoorSignal() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(3);
        doReturn(1).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);

        processAllFutureMessages();

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);
    }

    @Test
    public void testDoNotDoRecoveryActionWhenDialCall() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(3);
        doReturn(3).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.OFFHOOK).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);

        processAllFutureMessages();

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);
    }

    @Test
    public void testDoNotDoRecoveryBySendMessageDelayedWhenDialCall() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_VALID);
        mDataStallRecoveryManager.setRecoveryAction(0);
        doReturn(PhoneConstants.State.OFFHOOK).when(mPhone).getState();
        doReturn(3).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(1);
        mDataStallRecoveryManager.sendMessageDelayed(
                mDataStallRecoveryManager.obtainMessage(3), 1000);
        moveTimeForward(15000);
        processAllMessages();

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);
    }

    @Test
    public void testDoNotContinueRecoveryActionAfterModemReset() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_VALID);
        mDataStallRecoveryManager.setRecoveryAction(0);
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();
        doReturn(3).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        logd("Sending validation failed callback");

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(1);

        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);

        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(4);

        // Handle multiple VALIDATION_STATUS_NOT_VALID and make sure we don't attempt recovery
        for (int i = 0; i < 4; i++) {
            sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
            logd("Sending validation failed callback");
            processAllMessages();
            moveTimeForward(101);
            assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
        }
    }

    @Test
    public void testDoRecoveryWhenMeetDataStallAgain() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_VALID);
        mDataStallRecoveryManager.setRecoveryAction(0);
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();
        doReturn(3).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        logd("Sending validation failed callback");

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(1);

        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);

        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(4);

        // Handle multiple VALIDATION_STATUS_NOT_VALID and make sure we don't attempt recovery
        for (int i = 0; i < 4; i++) {
            sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
            logd("Sending validation failed callback");
            processAllMessages();
            moveTimeForward(101);
            assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
        }

        moveTimeForward(101);
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);

        mDataStallRecoveryManager.sendMessageDelayed(
                mDataStallRecoveryManager.obtainMessage(0), 1000);
        processAllMessages();
        processAllMessages();
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
    }

    @Test
    public void testDoNotDoRecoveryWhenDataNoService() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(1);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();
        doReturn(false).when(mDataNetworkController).isInternetDataAllowed();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(1);
    }

    @Test
    public void testDoNotDoRecoveryWhenDataNetworkNotConnected() throws Exception {
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(1);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();
        sendOnInternetDataNetworkCallback(false);

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(1);
    }

    @Test
    public void testDoNotDoRecoveryIfNoValidationPassedYet() throws Exception {
        sendOnInternetDataNetworkCallback(false);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        // Handle multiple VALIDATION_STATUS_NOT_VALID and make sure we don't attempt recovery
        for (int i = 0; i < 4; i++) {
            sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
            logd("Sending validation failed callback");
            processAllMessages();
            moveTimeForward(101);
            assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
        }
    }

    @Test
    public void testStartTimeNotZero() throws Exception {
        sendOnInternetDataNetworkCallback(false);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        for (int i = 0; i < 2; i++) {
            sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
            logd("Sending validation failed callback");
            processAllMessages();
            moveTimeForward(101);
        }
        assertThat(mDataStallRecoveryManager.mDataStallStartMs != 0).isTrue();
    }

    /**
     * Tests the DSRM process to send three intents for three action changes.
     */
    @Test
    public void testSendDSRMData() throws Exception {
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);

        logd("Set phone status to normal status.");
        sendOnInternetDataNetworkCallback(true);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        // Set the expected behavior of the DataStallRecoveryManager.
        logd("Start DSRM process, set action to 1");
        mDataStallRecoveryManager.setRecoveryAction(1);
        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();

        logd("Verify that the DataStallRecoveryManager sends the expected intents.");
        verify(mPhone.getContext(), times(3)).sendBroadcast(captorIntent.capture());
        logd(captorIntent.getAllValues().toString());
        for (int i = 0; i < captorIntent.getAllValues().size(); i++) {
            Intent intent = captorIntent.getAllValues().get(i);
            // Check and assert if intent is null
            assertNotNull(intent);
            // Check and assert if intent is not ACTION_DATA_STALL_DETECTED
            assertThat(intent.getAction()).isEqualTo(
                    TelephonyManager.ACTION_DATA_STALL_DETECTED);
            // Get the extra data
            Bundle bundle = (Bundle) intent.getExtra("EXTRA_DSRS_STATS_BUNDLE");
            // Check and assert if bundle is null
            assertNotNull(bundle);
            // Dump bundle data
            logd(bundle.toString());
            int size = bundle.size();
            logd("bundle size is " + size);
            // Check if bundle size is 19
            assertThat(size).isEqualTo(19);
        }
    }

    /**
     * Tests update action enable state and duration from global settings.
     */
    @Test
    public void testUpdateGlobalSettings() throws Exception {
        Field field = DataStallRecoveryManager.class.getDeclaredField("mPredictWaitingMillis");
        field.setAccessible(true);

        // Set duration to 10000/20000/30000/40000
        Settings.Global.putString(
                mFakeContentResolver, Settings.Global.DSRM_DURATION_MILLIS,
                "10000,20000,30000,40000,0");
        // Send onChange event with Settings.Global.DSRM_DURATION_MILLIS to fake ContentResolver
        mFakeContentResolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.DSRM_DURATION_MILLIS), null);
        processAllFutureMessages();
        // Verify that the durations are correct values.
        assertThat(mDataStallRecoveryManager.getDataStallRecoveryDelayMillis(0)).isEqualTo(10000L);
        assertThat(mDataStallRecoveryManager.getDataStallRecoveryDelayMillis(1)).isEqualTo(20000L);
        assertThat(mDataStallRecoveryManager.getDataStallRecoveryDelayMillis(2)).isEqualTo(30000L);
        assertThat(mDataStallRecoveryManager.getDataStallRecoveryDelayMillis(3)).isEqualTo(40000L);

        // Set action enable state to true/false/false/false/true
        Settings.Global.putString(
                mFakeContentResolver, Settings.Global.DSRM_ENABLED_ACTIONS,
                "true,false,false,false,true");
        // Send onChange event with Settings.Global.DSRM_ENABLED_ACTIONS to fake ContentResolver
        mFakeContentResolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.DSRM_ENABLED_ACTIONS), null);
        processAllFutureMessages();
        // Verify that the action enable state are correct values.
        assertThat(mDataStallRecoveryManager.shouldSkipRecoveryAction(0)).isEqualTo(false);
        assertThat(mDataStallRecoveryManager.shouldSkipRecoveryAction(1)).isEqualTo(true);
        assertThat(mDataStallRecoveryManager.shouldSkipRecoveryAction(2)).isEqualTo(true);
        assertThat(mDataStallRecoveryManager.shouldSkipRecoveryAction(3)).isEqualTo(true);
        assertThat(mDataStallRecoveryManager.shouldSkipRecoveryAction(4)).isEqualTo(false);
        // Check the predict waiting millis
        assertThat(field.get(mDataStallRecoveryManager)).isEqualTo(1000L);
        // Test predict waiting millis to rollback to 0 if there is no global duration and action
        // Set duration to empty
        Settings.Global.putString(
                mFakeContentResolver, Settings.Global.DSRM_DURATION_MILLIS,
                "");
        // Send onChange event with Settings.Global.DSRM_DURATION_MILLIS to fake ContentResolver
        mFakeContentResolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.DSRM_DURATION_MILLIS), null);
        processAllFutureMessages();
        // Set action to empty
        Settings.Global.putString(
                mFakeContentResolver, Settings.Global.DSRM_ENABLED_ACTIONS,
                "");
        // Send onChange event with Settings.Global.DSRM_ENABLED_ACTIONS to fake ContentResolver
        mFakeContentResolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.DSRM_ENABLED_ACTIONS), null);
        processAllFutureMessages();
        // Check if predict waiting millis is 0
        assertThat(field.get(mDataStallRecoveryManager)).isEqualTo(0L);
    }
}
