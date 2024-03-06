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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
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
    private static final int SCORE_TOLERANCE = 100;
    private static final int GOOD_RAT_SIGNAL_SCORE = 200;
    private static final int BAD_RAT_SIGNAL_SCORE = 50;
    // Mocked
    private AutoDataSwitchController.AutoDataSwitchControllerCallback mMockedPhoneSwitcherCallback;

    // Real
    private TelephonyDisplayInfo mGoodTelephonyDisplayInfo;
    private TelephonyDisplayInfo mBadTelephonyDisplayInfo;
    private int mDefaultDataSub;
    private AutoDataSwitchController mAutoDataSwitchControllerUT;
    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mGoodTelephonyDisplayInfo = new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_NR,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED, false /*roaming*/);
        mBadTelephonyDisplayInfo = new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE, false /*roaming*/);
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
            doReturn(mDataNetworkController).when(phone).getDataNetworkController();
            doReturn(mDataConfigManager).when(mDataNetworkController).getDataConfigManager();
            doAnswer(invocation -> phone.getSubId() == mDefaultDataSub)
                    .when(phone).isUserDataEnabled();
        }
        doReturn(new int[]{SUB_1, SUB_2}).when(mSubscriptionManagerService)
                .getActiveSubIdList(true);
        doAnswer(invocation -> {
            int subId = (int) invocation.getArguments()[0];
            return subId == SUB_1 ? PHONE_1 : PHONE_2;
        }).when(mSubscriptionManagerService).getPhoneId(anyInt());
        doAnswer(invocation -> {
            int subId = (int) invocation.getArguments()[0];

            if (!SubscriptionManager.isUsableSubIdValue(subId)) return null;

            int slotIndex = subId == SUB_1 ? PHONE_1 : PHONE_2;
            return new SubscriptionInfoInternal.Builder()
                    .setSimSlotIndex(slotIndex).setId(subId).build();
        }).when(mSubscriptionManagerService).getSubscriptionInfoInternal(anyInt());
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);

        // Change data config
        doReturn(true).when(mDataConfigManager).isPingTestBeforeAutoDataSwitchRequired();
        doReturn(10000L).when(mDataConfigManager)
                .getAutoDataSwitchAvailabilityStabilityTimeThreshold();
        doReturn(MAX_RETRY).when(mDataConfigManager).getAutoDataSwitchValidationMaxRetry();
        doReturn(SCORE_TOLERANCE).when(mDataConfigManager).getAutoDataSwitchScoreTolerance();
        doAnswer(invocation -> {
            TelephonyDisplayInfo displayInfo = (TelephonyDisplayInfo) invocation.getArguments()[0];
            SignalStrength signalStrength = (SignalStrength) invocation.getArguments()[1];
            if (displayInfo == mGoodTelephonyDisplayInfo
                    || signalStrength.getLevel() > SignalStrength.SIGNAL_STRENGTH_MODERATE) {
                return GOOD_RAT_SIGNAL_SCORE;
            }
            return BAD_RAT_SIGNAL_SCORE;
        }).when(mDataConfigManager).getAutoDataSwitchScore(any(TelephonyDisplayInfo.class),
                any(SignalStrength.class));

        setDefaultDataSubId(SUB_1);
        doReturn(PHONE_1).when(mPhoneSwitcher).getPreferredDataPhoneId();

        mAutoDataSwitchControllerUT = new AutoDataSwitchController(mContext, Looper.myLooper(),
                mPhoneSwitcher, mFeatureFlags, mMockedPhoneSwitcherCallback);

        doReturn(true).when(mFeatureFlags).autoSwitchAllowRoaming();
    }

    @After
    public void tearDown() throws Exception {
        mAutoDataSwitchControllerUT = null;
        mGoodTelephonyDisplayInfo = null;
        mBadTelephonyDisplayInfo = null;
        super.tearDown();
    }

    @Test
    public void testCancelSwitch_onPrimary() {
        // 0. When all conditions met
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();

        // Verify attempting to switch
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, true/*needValidation*/);

        // 1.1 Service state becomes not ideal - secondary lost its advantage score,
        // but primary is OOS, so continue to switch.
        clearInvocations(mMockedPhoneSwitcherCallback);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_POOR);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback, never())
                .onRequireCancelAnyPendingAutoSwitchValidation();

        // 1.2 Service state becomes not ideal - secondary lost its advantage score,
        // since primary is in service, no need to switch.
        clearInvocations(mMockedPhoneSwitcherCallback);
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_POOR);
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
    public void testRoaming_prefer_home_over_roam() {
        // DDS -> nDDS: Prefer Home over Roaming
        prepareIdealUsesNonDdsCondition();
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, true/*needValidation*/);

        // nDDS -> DDS: Prefer Home over Roaming
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);
    }

    @Test
    public void testRoaming_roaming_but_roam_disabled() {
        // Disable RAT + signalStrength base switching.
        doReturn(-1).when(mDataConfigManager).getAutoDataSwitchScoreTolerance();
        mAutoDataSwitchControllerUT = new AutoDataSwitchController(mContext, Looper.myLooper(),
                mPhoneSwitcher, mFeatureFlags, mMockedPhoneSwitcherCallback);

        // On primary phone
        // 1.1 Both roaming, user allow roaming on both phone, no need to switch.
        prepareIdealUsesNonDdsCondition();
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);

        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback, never()).onRequireValidation(anyInt(),
                anyBoolean()/*needValidation*/);

        // 1.2 Both roaming, but roaming is only allowed on the backup phone.
        doReturn(false).when(mPhone).getDataRoamingEnabled();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, true/*needValidation*/);

        // On backup phone
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        // 2.1 Both roaming, user allow roaming on both phone, prefer default.
        doReturn(true).when(mPhone).getDataRoamingEnabled();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);

        // 2.1 Both roaming, but roaming is only allowed on the default phone.
        doReturn(false).when(mPhone2).getDataRoamingEnabled();
        mAutoDataSwitchControllerUT.evaluateAutoDataSwitch(EVALUATION_REASON_DATA_SETTINGS_CHANGED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                false/*needValidation*/);
    }

    @Test
    public void testRoaming_same_roaming_condition_uses_rat_signalStrength() {
        doReturn(true).when(mFeatureFlags).autoDataSwitchRatSs();
        // On primary phone
        // 1. Both roaming, user allow roaming on both phone, uses RAT score to decide switch.
        prepareIdealUsesNonDdsCondition();
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, true/*needValidation*/);

        // On backup phone
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        // 2. Both roaming, user allow roaming on both phone, uses RAT score to decide switch.
        signalStrengthChanged(PHONE_1, SignalStrength.SIGNAL_STRENGTH_GREAT);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_POOR);
        displayInfoChanged(PHONE_1, mGoodTelephonyDisplayInfo);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);
    }

    @Test
    public void testCancelSwitch_onPrimary_rat_signalStrength() {
        // 4.1.1 Display info and signal strength on secondary phone became bad,
        // but primary is still OOS, so still switch to the secondary.
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_MODERATE);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback, never())
                .onRequireCancelAnyPendingAutoSwitchValidation();

        // 4.1.2 Display info and signal strength on secondary phone became bad,
        // but primary become service, then don't switch.
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_MODERATE);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();

        // 4.2 Display info on default phone became good just as the secondary
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        displayInfoChanged(PHONE_1, mGoodTelephonyDisplayInfo);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();

        // 4.3 Signal strength on default phone became just as good as the secondary
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();
        clearInvocations(mMockedPhoneSwitcherCallback);
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        signalStrengthChanged(PHONE_1, SignalStrength.SIGNAL_STRENGTH_GREAT);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();
    }

    @Test
    public void testOnNonDdsSwitchBackToPrimary() {
        // Disable Rat/SignalStrength based switch to test primary OOS based switch
        doReturn(-1).when(mDataConfigManager).getAutoDataSwitchScoreTolerance();
        mAutoDataSwitchControllerUT = new AutoDataSwitchController(mContext, Looper.myLooper(),
                mPhoneSwitcher, mFeatureFlags, mMockedPhoneSwitcherCallback);
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();

        prepareIdealUsesNonDdsCondition();
        // 1.1 service state changes - primary becomes available again, require validation
        serviceStateChanged(PHONE_1,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME/*need validate*/);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);

        clearInvocations(mMockedPhoneSwitcherCallback);
        prepareIdealUsesNonDdsCondition();
        // 1.2 service state changes - secondary becomes unavailable, NO need validation
        serviceStateChanged(PHONE_1,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME/*need validate*/);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_DENIED/*no need*/);
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
    public void testOnNonDdsSwitchBackToPrimary_rat_signalStrength() {
        doReturn(true).when(mFeatureFlags).autoDataSwitchRatSs();
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();

        prepareIdealUsesNonDdsCondition();
        // 4.1 Display info and signal strength on secondary phone became bad just as the default
        // Expect no switch since both phone has the same score.
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_POOR);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback, never()).onRequireValidation(anyInt(), anyBoolean());

        clearInvocations(mMockedPhoneSwitcherCallback);
        prepareIdealUsesNonDdsCondition();
        // 4.2 Display info and signal strength on secondary phone became worse than the default.
        // Expect to switch.
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        signalStrengthChanged(PHONE_1, SignalStrength.SIGNAL_STRENGTH_GREAT);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_POOR);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);
    }

    @Test
    public void testCancelSwitch_onSecondary() {
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        prepareIdealUsesNonDdsCondition();

        // attempts the switch back due to secondary not usable
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_DENIED);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                false/*needValidation*/);

        // cancel the switch back attempt due to secondary back to HOME
        clearInvocations(mMockedPhoneSwitcherCallback);
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireCancelAnyPendingAutoSwitchValidation();
    }

    @Test
    public void testStabilityCheckOverride() {
        // Starting stability check for switching to non-DDS
        prepareIdealUsesNonDdsCondition();
        processAllMessages();

        // Switch success, but the previous stability check is still pending
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();

        // Display info and signal strength on secondary phone became worse than the default.
        // Expect to switch back, and it should override the previous stability check
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        signalStrengthChanged(PHONE_1, SignalStrength.SIGNAL_STRENGTH_GREAT);
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_POOR);
        // process all messages include the delayed message
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(DEFAULT_PHONE_INDEX,
                true/*needValidation*/);
        verify(mMockedPhoneSwitcherCallback, never()).onRequireValidation(PHONE_2,
                true/*needValidation*/);
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
        doReturn(-1 /*Disable signal based switch for easy mock*/).when(mDataConfigManager)
                .getAutoDataSwitchScoreTolerance();
        mAutoDataSwitchControllerUT = new AutoDataSwitchController(mContext, Looper.myLooper(),
                mPhoneSwitcher, mFeatureFlags, mMockedPhoneSwitcherCallback);

        //1. DDS -> nDDS, verify callback doesn't require validation
        prepareIdealUsesNonDdsCondition();
        processAllFutureMessages();

        verify(mMockedPhoneSwitcherCallback).onRequireValidation(PHONE_2, false/*needValidation*/);

        //2. nDDS -> DDS, verify callback doesn't require validation
        doReturn(PHONE_2).when(mPhoneSwitcher).getPreferredDataPhoneId();
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
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

    @Test
    public void testSubscriptionChangedUnregister() {
        // Test single SIM loaded
        int modemCount = 2;
        doReturn(new int[]{SUB_2}).when(mSubscriptionManagerService)
                .getActiveSubIdList(true);
        mAutoDataSwitchControllerUT.notifySubscriptionsMappingChanged();
        processAllMessages();

        // Verify unregister from both slots since only 1 visible SIM is insufficient for switching
        verify(mDisplayInfoController, times(modemCount))
                .unregisterForTelephonyDisplayInfoChanged(any());
        verify(mSignalStrengthController, times(modemCount))
                .unregisterForSignalStrengthChanged(any());
        verify(mSST, times(modemCount)).unregisterForServiceStateChanged(any());

        // Test single -> Duel
        clearInvocations(mDisplayInfoController, mSignalStrengthController, mSST);
        doReturn(new int[]{SUB_1, SUB_2}).when(mSubscriptionManagerService)
                .getActiveSubIdList(true);
        mAutoDataSwitchControllerUT.notifySubscriptionsMappingChanged();
        processAllMessages();

        // Verify register on both slots
        for (int phoneId = 0; phoneId < modemCount; phoneId++) {
            verify(mDisplayInfoController).registerForTelephonyDisplayInfoChanged(any(),
                    eq(EVENT_DISPLAY_INFO_CHANGED), eq(phoneId));
            verify(mSignalStrengthController).registerForSignalStrengthChanged(any(),
                    eq(EVENT_SIGNAL_STRENGTH_CHANGED), eq(phoneId));
            verify(mSST).registerForServiceStateChanged(any(),
                    eq(EVENT_SERVICE_STATE_CHANGED), eq(phoneId));
        }
    }

    @Test
    public void testRatSignalStrengthSkipEvaluation() {
        // Verify the secondary phone is OOS and its score(0) is too low to justify the evaluation
        displayInfoChanged(PHONE_2, mBadTelephonyDisplayInfo);
        processAllFutureMessages();
        verify(mMockedPhoneSwitcherCallback, never())
                .onRequireCancelAnyPendingAutoSwitchValidation();
        verify(mMockedPhoneSwitcherCallback, never()).onRequireValidation(anyInt(), anyBoolean());
    }

    /**
     * Trigger conditions
     * 1. service state changes
     * 2. telephony display info changes
     * 3. signal strength changes
     * 4. data setting changes
     *      - user toggle data
     *      - user toggle auto switch feature
     * 5. default network changes
     *      - current network lost
     *      - network become active on non-cellular network
     */
    private void prepareIdealUsesNonDdsCondition() {
        // 1. service state changes
        serviceStateChanged(PHONE_2, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        serviceStateChanged(PHONE_1, NetworkRegistrationInfo
                .REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);

        // 2. telephony display info changes
        displayInfoChanged(PHONE_2, mGoodTelephonyDisplayInfo);
        displayInfoChanged(PHONE_1, mBadTelephonyDisplayInfo);

        // 3. signal strength changes
        signalStrengthChanged(PHONE_2, SignalStrength.SIGNAL_STRENGTH_GREAT);
        signalStrengthChanged(PHONE_1, SignalStrength.SIGNAL_STRENGTH_POOR);

        // 4.1 User data enabled on primary SIM
        doReturn(true).when(mPhone).isUserDataEnabled();
        doReturn(true).when(mPhone).getDataRoamingEnabled();

        // 4.2 Auto switch feature is enabled
        doReturn(true).when(mPhone2).getDataRoamingEnabled();
        doReturn(true).when(mPhone2).isDataAllowed();

        // 5. No default network
        mAutoDataSwitchControllerUT.updateDefaultNetworkCapabilities(null /*networkCapabilities*/);
    }

    private void signalStrengthChanged(int phoneId, int level) {
        SignalStrength ss = mock(SignalStrength.class);
        doReturn(level).when(ss).getLevel();
        doReturn(ss).when(mPhones[phoneId]).getSignalStrength();

        Message msg = mAutoDataSwitchControllerUT.obtainMessage(EVENT_SIGNAL_STRENGTH_CHANGED);
        msg.obj = new AsyncResult(phoneId, null, null);
        mAutoDataSwitchControllerUT.sendMessage(msg);
        processAllMessages();
    }
    private void displayInfoChanged(int phoneId, TelephonyDisplayInfo telephonyDisplayInfo) {
        doReturn(telephonyDisplayInfo).when(mDisplayInfoController).getTelephonyDisplayInfo();

        Message msg = mAutoDataSwitchControllerUT.obtainMessage(EVENT_DISPLAY_INFO_CHANGED);
        msg.obj = new AsyncResult(phoneId, null, null);
        mAutoDataSwitchControllerUT.sendMessage(msg);
        processAllMessages();
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
