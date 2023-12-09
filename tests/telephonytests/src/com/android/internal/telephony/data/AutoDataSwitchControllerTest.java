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

package com.android.internal.telephony.data;

import static android.telephony.SubscriptionManager.DEFAULT_PHONE_INDEX;

import static com.android.internal.telephony.data.AutoDataSwitchController.EVALUATION_REASON_DATA_SETTINGS_CHANGED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.NotificationManager;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AutoDataSwitchControllerTest extends TelephonyTest {
    private static final int EVENT_SERVICE_STATE_CHANGED = 1;
    private static final int EVENT_DISPLAY_INFO_CHANGED = 2;
    private static final int EVENT_EVALUATE_AUTO_SWITCH = 3;
    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 4;
    private static final int EVENT_MEETS_AUTO_DATA_SWITCH_STATE = 5;

    private static final int PHONE_1 = 0;
    private static final int SUB_1 = 1;
    private static final int PHONE_2 = 1;
    private static final int SUB_2 = 2;
    private static final int MAX_RETRY = 5;
    // Mocked
    private AutoDataSwitchController.AutoDataSwitchControllerCallback mMockedPhoneSwitcherCallback;

    private int mDefaultDataSub;
    private AutoDataSwitchController mAutoDataSwitchControllerUT;
    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockedPhoneSwitcherCallback =
                mock(AutoDataSwitchController.AutoDataSwitchControllerCallback.class);

        doReturn(PHONE_1).when(mPhone).getPhoneId();
        doReturn(SUB_1).when(mPhone).getSubId();

        doReturn(PHONE_2).when(mPhone2).getPhoneId();
        doReturn(SUB_2).when(mPhone2).getSubId();

        doReturn(SUB_1).when(mSubscriptionManagerService).getSubId(PHONE_1);
        doReturn(SUB_2).when(mSubscriptionManagerService).getSubId(PHONE_2);

        mPhones = new Phone[]{mPhone, mPhone2};
        for (Phone phone : mPhones) {
            doReturn(mSST).when(phone).getServiceStateTracker();
            doReturn(mDisplayInfoController).when(phone).getDisplayInfoController();
            doReturn(mSignalStrengthController).when(phone).getSignalStrengthController();
            doReturn(mSignalStrength).when(phone).getSignalStrength();
            doAnswer(invocation -> phone.getSubId() == mDefaultDataSub)
                    .when(phone).isUserDataEnabled();
        }
        doReturn(new int[mPhones.length]).when(mSubscriptionManagerService)
                .getActiveSubIdList(true);
        doAnswer(invocation -> {
            int subId = (int) invocation.getArguments()[0];

            if (!SubscriptionManager.isUsableSubIdValue(subId)) return null;

            int slotIndex = subId == SUB_1 ? PHONE_1 : PHONE_2;
            return new SubscriptionInfoInternal.Builder()
                    .setSimSlotIndex(slotIndex).setId(subId).build();
        }).when(mSubscriptionManagerService).getSubscriptionInfoInternal(anyInt());
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);

        // Change resource overlay
        doReturn(true).when(mDataConfigManager).isPingTestBeforeAutoDataSwitchRequired();
        doReturn(1L).when(mDataConfigManager)
                .getAutoDataSwitchAvailabilityStabilityTimeThreshold();
        doReturn(MAX_RETRY).when(mDataConfigManager).getAutoDataSwitchValidationMaxRetry();

        setDefaultDataSubId(SUB_1);
        doReturn(PHONE_1).when(mPhoneSwitcher).getPreferredDataPhoneId();

        mAutoDataSwitchControllerUT = new AutoDataSwitchController(mContext, Looper.myLooper(),
                mPhoneSwitcher, mMockedPhoneSwitcherCallback);
    }

    @After
    public void tearDown() throws Exception {
        mAutoDataSwitchControllerUT = null;
        super.tearDown();
    }

    @Test
    public void testCancelSwitch_onPrimary() {
        // 0. When all conditions met
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();

        // Verify attempting to switch
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, true/*needValidation*/);

        // 1. Service state becomes not ideal - primary is available again
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();

        // 2.1 User data disabled on primary SIM
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        doReturn(false).when(mPhone).isUserDataEnabled();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();

        // 2.2 Auto switch feature is disabled
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        doReturn(false).when(mPhone2).isDataAllowed();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();

        // 3.1 No default network
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        mAutoDataSwitchControllerUT.updateDefaultNetworkCapabilities(new NetworkCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI));
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();
    }

    @Test
    public void testOnNonDdsSwitchBackToPrimary() {
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();

        prepareIdealUsesNonDdsCondition();
        // 1.1 service state changes - primary becomes available again, require validation
        serviceStateChanged(PHONE_1,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING/*need validate*/);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);

        clearInvocations(mMockedPhoneSwitcherCallback);
        prepareIdealUsesNonDdsCondition();
        // 1.2 service state changes - secondary becomes unavailable, NO need validation
        serviceStateChanged(PHONE_1,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME/*need validate*/);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING/*no need*/);
        processAllFutureMessages();
        // The later validation requirement overrides the previous
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                false/*needValidation*/);

        clearInvocations(mMockedPhoneSwitcherCallback);
        prepareIdealUsesNonDdsCondition();
        // 2.1 User data disabled on primary SIM, no need validation
        doReturn(false).when(mPhone).isUserDataEnabled();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireImmediatelySwitchToPhone(DEFAULT_PHONE_INDEX,
                EVALUATION_REASON_DATA_SETTINGS_CHANGED);

        clearInvocations(mMockedPhoneSwitcherCallback);
        prepareIdealUsesNonDdsCondition();
        // 2.2 Auto switch feature is disabled, no need validation
        clearInvocations(mCellularNetworkValidator);
        doReturn(false).when(mPhone2).isDataAllowed();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireImmediatelySwitchToPhone(DEFAULT_PHONE_INDEX,
                EVALUATION_REASON_DATA_SETTINGS_CHANGED);

        clearInvocations(mMockedPhoneSwitcherCallback);
        prepareIdealUsesNonDdsCondition();
        // 3.1 Default network is active on non-cellular transport
        mAutoDataSwitchControllerUT.updateDefaultNetworkCapabilities(new NetworkCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI));
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                false/*needValidation*/);
    }

    @Test
    public void testCancelSwitch_onSecondary() {
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        prepareIdealUsesNonDdsCondition();

        // attempts the switch back due to secondary becomes ROAMING
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                false/*needValidation*/);

        // cancel the switch back attempt due to secondary back to HOME
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();
    }

    @Test
    public void testValidationFailedRetry() {
        prepareIdealUsesNonDdsCondition();

        for (int i = 0; i < MAX_RETRY; i++) {
            mAutoDataSwitchControllerUT.evaluateRetryOnValidationFailed();
            processAllFutureMessages();
        }
        verify(mMockedPhoneSwitcherCallback, times(MAX_RETRY))
                .onRequireValidation(PHONE_2, true /*need validation*/);
    }

    @Test
    public void testExemptPingTest() {
        // Change resource overlay
        doReturn(false).when(mDataConfigManager)
                .isPingTestBeforeAutoDataSwitchRequired();
        mAutoDataSwitchControllerUT = new AutoDataSwitchController(mContext, Looper.myLooper(),
                mPhoneSwitcher, mMockedPhoneSwitcherCallback);

        //1. DDS -> nDDS, verify callback doesn't require validation
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, false/*needValidation*/);

        //2. nDDS -> DDS, verify callback doesn't require validation
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                false/*needValidation*/);
    }

    @Test
    public void testSetNotification() {
        NotificationManager notificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        SubscriptionInfo mockedInfo = mock(SubscriptionInfo.class);
        doReturn(false).when(mockedInfo).isOpportunistic();
        doReturn(mockedInfo).when(mSubscriptionManagerService).getSubscriptionInfo(anyInt());

        // First switch is not due to auto, so no notification.
        mAutoDataSwitchControllerUT.displayAutoDataSwitchNotification(PHONE_2, false);
        verify(mSubscriptionManagerService, never()).getSubscriptionInfo(SUB_2);

        // Switch is due to auto, show notification.
        mAutoDataSwitchControllerUT.displayAutoDataSwitchNotification(PHONE_2, true);
        verify(notificationManager).notify(any(), anyInt(), any());
        verify(mSubscriptionManagerService).getSubscriptionInfo(SUB_2);

        // Switch is due to auto, but already shown notification, hide the notification.
        mAutoDataSwitchControllerUT.displayAutoDataSwitchNotification(PHONE_2, true);
        verify(notificationManager).cancel(any(), anyInt());
    }

    @Test
    public void testMultiSimConfigChanged() {
        // Test Dual -> Single
        mAutoDataSwitchControllerUT.onMultiSimConfigChanged(1);

        verify(mDisplayInfoController).unregisterForTelephonyDisplayInfoChanged(any());
        verify(mSignalStrengthController).unregisterForSignalStrengthChanged(any());
        verify(mSST).unregisterForServiceStateChanged(any());

        clearInvocations(mDisplayInfoController, mSignalStrengthController, mSST);
        // Test Single -> Dual
        mAutoDataSwitchControllerUT.onMultiSimConfigChanged(2);

        verify(mDisplayInfoController).registerForTelephonyDisplayInfoChanged(any(),
                eq(EVENT_DISPLAY_INFO_CHANGED), eq(PHONE_2));
        verify(mSignalStrengthController).registerForSignalStrengthChanged(any(),
                eq(EVENT_SIGNAL_STRENGTH_CHANGED), eq(PHONE_2));
        verify(mSST).registerForServiceStateChanged(any(),
                eq(EVENT_SERVICE_STATE_CHANGED), eq(PHONE_2));
    }

    /**
     * Trigger conditions
     * 1. service state changes
     * 2. data setting changes
     *      - user toggle data
     *      - user toggle auto switch feature
     * 3. default network changes
     *      - current network lost
     *      - network become active on non-cellular network
     */
    private void prepareIdealUsesNonDdsCondition() {
        // 1. service state changes
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo
                .REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);

        // 2.1 User data enabled on primary SIM
        doReturn(true).when(mPhone).isUserDataEnabled();

        // 2.2 Auto switch feature is enabled
        doReturn(true).when(mPhone2).isDataAllowed();

        // 3.1 No default network
        mAutoDataSwitchControllerUT.updateDefaultNetworkCapabilities(null /*networkCapabilities*/);
    }

    private void serviceStateChanged(int phoneId,
            @NetworkRegistrationInfo.RegistrationState int dataRegState) {

        ServiceState ss = new ServiceState();

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(dataRegState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.setDataRoamingFromRegistration(dataRegState
                == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);

        doReturn(ss).when(mPhones[phoneId]).getServiceState();

        Message msg = mAutoDataSwitchControllerUT.obtainMessage(EVENT_SERVICE_STATE_CHANGED);
        msg.obj = new AsyncResult(phoneId, null, null);
        mAutoDataSwitchControllerUT.sendMessage(msg);
    }
    private void setDefaultDataSubId(int defaultDataSub) {
        mDefaultDataSub = defaultDataSub;
        doReturn(mDefaultDataSub).when(mSubscriptionManagerService).getDefaultDataSubId();
    }
}
