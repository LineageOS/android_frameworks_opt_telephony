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

import static com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.net.NetworkAgent;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataStallRecoveryManager.DataStallRecoveryManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataStallRecoveryManagerTest extends TelephonyTest {

    @Mock private DataNetworkController mDataNetworkController;
    @Mock private DataServiceManager mDataServiceManager;
    @Mock private DataStallRecoveryManagerCallback mDataStallRecoveryManagerCallback;
    private DataStallRecoveryManager mDataStallRecoveryManager;

    @Before
    public void setUp() throws Exception {
        logd("DataStallRecoveryManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        doReturn(mDataNetworkController).when(mPhone).getDataNetworkController();
        doReturn(mDataConfigManager).when(mDataNetworkController).getDataConfigManager();
        doAnswer(
                invocation -> {
                    ((Runnable) invocation.getArguments()[0]).run();
                    return null;
                })
                .when(mDataStallRecoveryManagerCallback)
                .invokeFromExecutor(any(Runnable.class));

        mDataStallRecoveryManager =
                new DataStallRecoveryManager(
                        mPhone,
                        mDataNetworkController,
                        mDataServiceManager,
                        mTestableLooper.getLooper(),
                        mDataStallRecoveryManagerCallback);
        logd("DataStallRecoveryManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void sendValidationFailedCallback() throws Exception {
        ArgumentCaptor<DataNetworkControllerCallback> dataNetworkControllerCallbackCaptor =
                ArgumentCaptor.forClass(DataNetworkControllerCallback.class);
        verify(mDataNetworkController)
                .registerDataNetworkControllerCallback(
                        any(Executor.class),
                        dataNetworkControllerCallbackCaptor.capture(),
                        eq(false));
        DataNetworkControllerCallback dataNetworkControllerCallback =
                dataNetworkControllerCallbackCaptor.getValue();
        dataNetworkControllerCallback.onInternetDataNetworkValidationStatusChanged(
                NetworkAgent.VALIDATION_STATUS_NOT_VALID);
    }

    @Test
    public void testRecoveryStepPDPReset() throws Exception {
        mDataStallRecoveryManager.setRecoveryAction(1);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationFailedCallback();
        processAllMessages();
        moveTimeForward(180000);

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(2);
        verify(mDataStallRecoveryManagerCallback).onDataStallReestablishInternet();
    }

    @Test
    public void testRecoveryStepRestartRadio() throws Exception {
        mDataStallRecoveryManager.setRecoveryAction(2);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationFailedCallback();
        processAllMessages();
        moveTimeForward(180000);

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(3);
    }

    @Test
    public void testRecoveryStepModemReset() throws Exception {
        mDataStallRecoveryManager.setRecoveryAction(3);
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationFailedCallback();

        processAllMessages();
        moveTimeForward(180000);

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
    }

    @Test
    public void testDoNotDoRecoveryActionWhenPoorSignal() throws Exception {
        mDataStallRecoveryManager.setRecoveryAction(2);
        doReturn(1).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.IDLE).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationFailedCallback();

        processAllMessages();
        moveTimeForward(180000);

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(0);
    }

    @Test
    public void testDoNotDoRecoveryActionWhenDialCall() throws Exception {
        mDataStallRecoveryManager.setRecoveryAction(2);
        doReturn(3).when(mSignalStrength).getLevel();
        doReturn(mSignalStrength).when(mPhone).getSignalStrength();
        doReturn(PhoneConstants.State.OFFHOOK).when(mPhone).getState();

        logd("Sending validation failed callback");
        sendValidationFailedCallback();

        processAllMessages();
        moveTimeForward(180000);

        assertThat(mDataStallRecoveryManager.getRecoveryAction()).isEqualTo(2);
    }
}
