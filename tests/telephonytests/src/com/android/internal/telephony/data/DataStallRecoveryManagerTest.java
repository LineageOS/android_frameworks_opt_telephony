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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.NetworkAgent;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.CarrierConfigManager;
import android.telephony.data.DataProfile;
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

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataStallRecoveryManagerTest extends TelephonyTest {
    // Mocked classes
    private DataStallRecoveryManagerCallback mDataStallRecoveryManagerCallback;

    private DataStallRecoveryManager mDataStallRecoveryManager;

    @Before
    public void setUp() throws Exception {
        logd("DataStallRecoveryManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
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

        doAnswer(
                invocation -> {
                    ((Runnable) invocation.getArguments()[0]).run();
                    return null;
                })
                .when(mDataStallRecoveryManagerCallback)
                .invokeFromExecutor(any(Runnable.class));
        doReturn("").when(mSubscriptionController).getDataEnabledOverrideRules(anyInt());

        mDataStallRecoveryManager =
                new DataStallRecoveryManager(
                        mPhone,
                        mDataNetworkController,
                        mMockedWwanDataServiceManager,
                        mTestableLooper.getLooper(),
                        mDataStallRecoveryManagerCallback);
        logd("DataStallRecoveryManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mDataStallRecoveryManager = null;
        super.tearDown();
    }

    private void sendValidationStatusCallback(@ValidationStatus int status) {
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController)
                .registerDataNetworkControllerCallback(
                        dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback dataNetworkControllerCallback =
                dataNetworkControllerCallbackCaptor.getValue();
        dataNetworkControllerCallback.onInternetDataNetworkValidationStatusChanged(status);
    }

    private void sendOnInternetDataNetworkCallback(boolean isConnected) {
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController)
                .registerDataNetworkControllerCallback(
                        dataNetworkControllerCallbackCaptor.capture());
        DataNetworkControllerCallback dataNetworkControllerCallback =
                dataNetworkControllerCallbackCaptor.getValue();

        if (isConnected) {
            List<DataProfile> dataprofile = new ArrayList<DataProfile>();
            dataNetworkControllerCallback.onInternetDataNetworkConnected(dataprofile);
        } else {
            dataNetworkControllerCallback.onInternetDataNetworkDisconnected();
        }
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
    public void testNextRecoveryAfterSkippingUnderPoorSignal() throws Exception {
        // Test to validate if the next recovery action is performed in good signal
        // soon after skipping the recovery action under poor signal condition
        sendOnInternetDataNetworkCallback(true);
        mDataStallRecoveryManager.setRecoveryAction(1);
        doReturn(1).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);

        // verify skipping recovery action under poor signal condition
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(1);

        // Set the signal condition to good
        doReturn(3).when(mSignalStrength).getLevel();

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllMessages();
        moveTimeForward(101);

        // verify next recovery action is performed under good signal condition
        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);
    }

    @Test
    public void testDoNotRecoveryForAlwaysInvalidNetwork() throws Exception {
        // Test to verify that recovery action is not performed for always invalid network
        // In some lab testing scenarios, n/w validation always remain invalid.
        sendOnInternetDataNetworkCallback(false);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();
        mDataStallRecoveryManager
                .setRecoveryAction(DataStallRecoveryManager.RECOVERY_ACTION_GET_DATA_CALL_LIST);

        logd("Sending validation failed callback");
        sendValidationStatusCallback(NetworkAgent.VALIDATION_STATUS_NOT_VALID);
        processAllFutureMessages();
        moveTimeForward(101);

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
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
}
